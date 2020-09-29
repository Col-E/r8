// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.ResourceException;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfPosition;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexDebugEvent;
import com.android.tools.r8.graph.DexDebugEvent.AdvancePC;
import com.android.tools.r8.graph.DexDebugEvent.Default;
import com.android.tools.r8.graph.DexDebugEvent.EndLocal;
import com.android.tools.r8.graph.DexDebugEvent.RestartLocal;
import com.android.tools.r8.graph.DexDebugEvent.SetEpilogueBegin;
import com.android.tools.r8.graph.DexDebugEvent.SetFile;
import com.android.tools.r8.graph.DexDebugEvent.SetPrologueEnd;
import com.android.tools.r8.graph.DexDebugEvent.StartLocal;
import com.android.tools.r8.graph.DexDebugEventBuilder;
import com.android.tools.r8.graph.DexDebugEventVisitor;
import com.android.tools.r8.graph.DexDebugInfo;
import com.android.tools.r8.graph.DexDebugPositionState;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue.DexValueString;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.kotlin.KotlinSourceDebugExtensionParser;
import com.android.tools.r8.kotlin.KotlinSourceDebugExtensionParser.Result;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.naming.ClassNaming;
import com.android.tools.r8.naming.ClassNaming.Builder;
import com.android.tools.r8.naming.MemberNaming;
import com.android.tools.r8.naming.MemberNaming.FieldSignature;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.naming.Range;
import com.android.tools.r8.naming.mappinginformation.FileNameInformation;
import com.android.tools.r8.retrace.RetraceUtils;
import com.android.tools.r8.shaking.KeepInfoCollection;
import com.android.tools.r8.utils.InternalOptions.LineNumberOptimization;
import com.google.common.base.Suppliers;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

public class LineNumberOptimizer {

  // PositionRemapper is a stateful function which takes a position (represented by a
  // DexDebugPositionState) and returns a remapped Position.
  private interface PositionRemapper {
    Pair<Position, Position> createRemappedPosition(Position position);
  }

  private static class IdentityPositionRemapper implements PositionRemapper {

    @Override
    public Pair<Position, Position> createRemappedPosition(Position position) {
      return new Pair<>(position, position);
    }
  }

  private static class OptimizingPositionRemapper implements PositionRemapper {
    private final int maxLineDelta;
    private DexMethod previousMethod = null;
    private int previousSourceLine = -1;
    private int nextOptimizedLineNumber = 1;

    OptimizingPositionRemapper(InternalOptions options) {
      // TODO(113198295): For dex using "Constants.DBG_LINE_RANGE + Constants.DBG_LINE_BASE"
      // instead of 1 creates a ~30% smaller map file but the dex files gets larger due to reduced
      // debug info canonicalization.
      maxLineDelta = options.isGeneratingClassFiles() ? Integer.MAX_VALUE : 1;
    }

    @Override
    public Pair<Position, Position> createRemappedPosition(Position position) {
      assert position.method != null;
      if (previousMethod == position.method) {
        assert previousSourceLine >= 0;
        if (position.line > previousSourceLine
            && position.line - previousSourceLine <= maxLineDelta) {
          nextOptimizedLineNumber += (position.line - previousSourceLine) - 1;
        }
      }

      Position newPosition =
          new Position(nextOptimizedLineNumber, position.file, position.method, null);
      ++nextOptimizedLineNumber;
      previousSourceLine = position.line;
      previousMethod = position.method;
      return new Pair<>(position, newPosition);
    }
  }

  private static class KotlinInlineFunctionPositionRemapper implements PositionRemapper {

    private final AppView<?> appView;
    private final DexItemFactory factory;
    private final Map<DexType, Result> parsedKotlinSourceDebugExtensions = new IdentityHashMap<>();
    private final CfLineToMethodMapper lineToMethodMapper;
    private final PositionRemapper baseRemapper;

    // Fields for the current context.
    private DexEncodedMethod currentMethod;
    private Result parsedData = null;

    private KotlinInlineFunctionPositionRemapper(
        AppView<?> appView,
        PositionRemapper baseRemapper,
        CfLineToMethodMapper lineToMethodMapper) {
      this.appView = appView;
      this.factory = appView.dexItemFactory();
      this.baseRemapper = baseRemapper;
      this.lineToMethodMapper = lineToMethodMapper;
    }

    @Override
    public Pair<Position, Position> createRemappedPosition(Position position) {
      assert currentMethod != null;
      int line = position.line;
      Result parsedData = getAndParseSourceDebugExtension(position.method.holder);
      if (parsedData == null) {
        return baseRemapper.createRemappedPosition(position);
      }
      Map.Entry<Integer, KotlinSourceDebugExtensionParser.Position> currentPosition =
          parsedData.lookup(line);
      if (currentPosition == null) {
        return baseRemapper.createRemappedPosition(position);
      }
      int delta = line - currentPosition.getKey();
      int originalPosition = currentPosition.getValue().getRange().from + delta;
      try {
        String binaryName = currentPosition.getValue().getSource().getPath();
        String nameAndDescriptor =
            lineToMethodMapper.lookupNameAndDescriptor(binaryName, originalPosition);
        if (nameAndDescriptor == null) {
          return baseRemapper.createRemappedPosition(position);
        }
        String clazzDescriptor = DescriptorUtils.getDescriptorFromClassBinaryName(binaryName);
        String methodName = CfLineToMethodMapper.getName(nameAndDescriptor);
        String methodDescriptor = CfLineToMethodMapper.getDescriptor(nameAndDescriptor);
        String returnTypeDescriptor = DescriptorUtils.getReturnTypeDescriptor(methodDescriptor);
        String[] argumentDescriptors = DescriptorUtils.getArgumentTypeDescriptors(methodDescriptor);
        DexString[] argumentDexStringDescriptors = new DexString[argumentDescriptors.length];
        for (int i = 0; i < argumentDescriptors.length; i++) {
          argumentDexStringDescriptors[i] = factory.createString(argumentDescriptors[i]);
        }
        DexMethod inlinee =
            factory.createMethod(
                factory.createString(clazzDescriptor),
                factory.createString(methodName),
                factory.createString(returnTypeDescriptor),
                argumentDexStringDescriptors);
        if (!inlinee.equals(position.method)) {
          return baseRemapper.createRemappedPosition(
              new Position(originalPosition, null, inlinee, position));
        }
        // This is the same position, so we should really not mark this as an inline position. Fall
        // through to the default case.
      } catch (ResourceException ignored) {
        // Intentionally left empty. Remapping of kotlin functions utility is a best effort mapping.
      }
      return baseRemapper.createRemappedPosition(position);
    }

    private Result getAndParseSourceDebugExtension(DexType holder) {
      if (parsedData == null) {
        parsedData = parsedKotlinSourceDebugExtensions.get(holder);
      }
      if (parsedData != null || parsedKotlinSourceDebugExtensions.containsKey(holder)) {
        return parsedData;
      }
      DexClass clazz = appView.definitionFor(currentMethod.holder());
      DexValueString dexValueString = appView.getSourceDebugExtensionForType(clazz);
      if (dexValueString != null) {
        parsedData = KotlinSourceDebugExtensionParser.parse(dexValueString.value.toString());
      }
      parsedKotlinSourceDebugExtensions.put(holder, parsedData);
      return parsedData;
    }

    public void setMethod(DexEncodedMethod method) {
      this.currentMethod = method;
      this.parsedData = null;
    }
  }

  // PositionEventEmitter is a stateful function which converts a Position into series of
  // position-related DexDebugEvents and puts them into a processedEvents list.
  private static class PositionEventEmitter {
    private final DexItemFactory dexItemFactory;
    private int startLine = -1;
    private final DexMethod method;
    private int previousPc = 0;
    private Position previousPosition = null;
    private final List<DexDebugEvent> processedEvents;

    private PositionEventEmitter(
        DexItemFactory dexItemFactory, DexMethod method, List<DexDebugEvent> processedEvents) {
      this.dexItemFactory = dexItemFactory;
      this.method = method;
      this.processedEvents = processedEvents;
    }

    private void emitAdvancePc(int pc) {
      processedEvents.add(new AdvancePC(pc - previousPc));
      previousPc = pc;
    }

    private void emitPositionEvents(int currentPc, Position currentPosition) {
      if (previousPosition == null) {
        startLine = currentPosition.line;
        previousPosition = new Position(startLine, null, method, null);
      }
      DexDebugEventBuilder.emitAdvancementEvents(
          previousPc,
          previousPosition,
          currentPc,
          currentPosition,
          processedEvents,
          dexItemFactory,
          true);
      previousPc = currentPc;
      previousPosition = currentPosition;
    }

    private int getStartLine() {
      assert (startLine >= 0);
      return startLine;
    }
  }

  // We will be remapping positional debug events and collect them as MappedPositions.
  private static class MappedPosition {
    private final DexMethod method;
    private final int originalLine;
    private final Position caller;
    private final int obfuscatedLine;

    private MappedPosition(
        DexMethod method, int originalLine, Position caller, int obfuscatedLine) {
      this.method = method;
      this.originalLine = originalLine;
      this.caller = caller;
      this.obfuscatedLine = obfuscatedLine;
    }
  }

  public static ClassNameMapper run(
      AppView<AppInfoWithClassHierarchy> appView,
      DexApplication application,
      AndroidApp inputApp,
      NamingLens namingLens) {
    // For finding methods in kotlin files based on SourceDebugExtensions, we use a line method map.
    // We create it here to ensure it is only reading class files once.
    CfLineToMethodMapper cfLineToMethodMapper = new CfLineToMethodMapper(inputApp);
    ClassNameMapper.Builder classNameMapperBuilder = ClassNameMapper.builder();
    // Collect which files contain which classes that need to have their line numbers optimized.
    for (DexProgramClass clazz : application.classes()) {
      IdentityHashMap<DexString, List<DexEncodedMethod>> methodsByRenamedName =
          groupMethodsByRenamedName(appView.graphLens(), namingLens, clazz);

      // At this point we don't know if we really need to add this class to the builder.
      // It depends on whether any methods/fields are renamed or some methods contain positions.
      // Create a supplier which creates a new, cached ClassNaming.Builder on-demand.
      DexType originalType = appView.graphLens().getOriginalType(clazz.type);
      DexString renamedClassName = namingLens.lookupDescriptor(clazz.getType());
      Supplier<ClassNaming.Builder> onDemandClassNamingBuilder =
          Suppliers.memoize(
              () ->
                  classNameMapperBuilder.classNamingBuilder(
                      DescriptorUtils.descriptorToJavaType(renamedClassName.toString()),
                      originalType.toSourceString(),
                      com.android.tools.r8.position.Position.UNKNOWN));

      // Check if source file should be added to the map
      if (clazz.sourceFile != null) {
        String sourceFile = clazz.sourceFile.toString();
        if (!RetraceUtils.hasPredictableSourceFileName(clazz.toSourceString(), sourceFile)) {
          Builder builder = onDemandClassNamingBuilder.get();
          builder.addMappingInformation(FileNameInformation.build(sourceFile));
        }
      }

      // If the class is renamed add it to the classNamingBuilder.
      addClassToClassNaming(originalType, renamedClassName, onDemandClassNamingBuilder);

      // First transfer renamed fields to classNamingBuilder.
      addFieldsToClassNaming(
          appView.graphLens(), namingLens, clazz, originalType, onDemandClassNamingBuilder);

      // Then process the methods, ordered by renamed name.
      List<DexString> renamedMethodNames = new ArrayList<>(methodsByRenamedName.keySet());
      renamedMethodNames.sort(DexString::slowCompareTo);
      for (DexString methodName : renamedMethodNames) {
        List<DexEncodedMethod> methods = methodsByRenamedName.get(methodName);
        if (methods.size() > 1) {
          // If there are multiple methods with the same name (overloaded) then sort them for
          // deterministic behaviour: the algorithm will assign new line numbers in this order.
          // Methods with different names can share the same line numbers, that's why they don't
          // need to be sorted.
          // If we are compiling to DEX we will try to not generate overloaded names. This saves
          // space by allowing more debug-information to be canonicalized. If we have overloaded
          // methods, we either did not rename them, we renamed them according to a supplied map or
          // they may be bridges for interface methods with covariant return types.
          sortMethods(methods);
          assert verifyMethodsAreKeptDirectlyOrIndirectly(appView, methods);
        }

        boolean identityMapping =
            appView.options().lineNumberOptimization == LineNumberOptimization.OFF;
        PositionRemapper positionRemapper =
            identityMapping
                ? new IdentityPositionRemapper()
                : new OptimizingPositionRemapper(appView.options());

        // Kotlin inline functions and arguments have their inlining information stored in the
        // source debug extension annotation. Instantiate the kotlin remapper on top of the original
        // remapper to allow for remapping original positions to kotlin inline positions.
        KotlinInlineFunctionPositionRemapper kotlinRemapper =
            new KotlinInlineFunctionPositionRemapper(
                appView, positionRemapper, cfLineToMethodMapper);

        for (DexEncodedMethod method : methods) {
          kotlinRemapper.currentMethod = method;
          List<MappedPosition> mappedPositions = new ArrayList<>();
          Code code = method.getCode();
          if (code != null) {
            if (code.isDexCode() && doesContainPositions(code.asDexCode())) {
              if (appView.options().canUseDexPcAsDebugInformation() && methods.size() == 1) {
                optimizeDexCodePositionsForPc(method, kotlinRemapper, mappedPositions);
              } else {
                optimizeDexCodePositions(
                    method, appView, kotlinRemapper, mappedPositions, identityMapping);
              }
            } else if (code.isCfCode()
                && doesContainPositions(code.asCfCode())
                && !appView.isCfByteCodePassThrough(method)) {
              optimizeCfCodePositions(method, kotlinRemapper, mappedPositions, appView);
            }
          }

          DexMethod originalMethod = appView.graphLens().getOriginalMethodSignature(method.method);
          MethodSignature originalSignature =
              MethodSignature.fromDexMethod(originalMethod, originalMethod.holder != originalType);

          DexString obfuscatedNameDexString = namingLens.lookupName(method.method);
          String obfuscatedName = obfuscatedNameDexString.toString();

          // Add simple "a() -> b" mapping if we won't have any other with concrete line numbers
          if (mappedPositions.isEmpty()) {
            // But only if it's been renamed.
            if (obfuscatedNameDexString != originalMethod.name
                || originalMethod.holder != originalType) {
              onDemandClassNamingBuilder
                  .get()
                  .addMappedRange(null, originalSignature, null, obfuscatedName);
            }
            continue;
          }

          Map<DexMethod, MethodSignature> signatures = new IdentityHashMap<>();
          signatures.put(originalMethod, originalSignature);
          Function<DexMethod, MethodSignature> getOriginalMethodSignature =
              m ->
                  signatures.computeIfAbsent(
                      m, key -> MethodSignature.fromDexMethod(m, m.holder != clazz.getType()));

          MemberNaming memberNaming = new MemberNaming(originalSignature, obfuscatedName);
          onDemandClassNamingBuilder.get().addMemberEntry(memberNaming);

          // Update memberNaming with the collected positions, merging multiple positions into a
          // single region whenever possible.
          for (int i = 0; i < mappedPositions.size(); /* updated in body */ ) {
            MappedPosition firstPosition = mappedPositions.get(i);
            int j = i + 1;
            MappedPosition lastPosition = firstPosition;
            for (; j < mappedPositions.size(); j++) {
              // Break if this position cannot be merged with lastPosition.
              MappedPosition mp = mappedPositions.get(j);
              // We allow for ranges being mapped to the same line but not to other ranges:
              //   1:10:void foo():42:42 -> a
              // is OK since retrace(a(:7)) = 42, however, the following is not OK:
              //   1:10:void foo():42:43 -> a
              // since retrace(a(:7)) = 49, which is not correct.
              boolean isSingleLine = mp.originalLine == firstPosition.originalLine;
              boolean differentDelta =
                  mp.originalLine - lastPosition.originalLine
                      != mp.obfuscatedLine - lastPosition.obfuscatedLine;
              boolean isMappingRangeToSingleLine =
                  firstPosition.obfuscatedLine != lastPosition.obfuscatedLine
                      && firstPosition.originalLine == lastPosition.originalLine;
              // Note that mp.caller and lastPosition.class must be deep-compared since multiple
              // inlining passes lose the canonical property of the positions.
              if (mp.method != lastPosition.method
                  || (!isSingleLine && differentDelta)
                  || (!isSingleLine && isMappingRangeToSingleLine)
                  || !Objects.equals(mp.caller, lastPosition.caller)) {
                break;
              }
              // The mapped positions are not guaranteed to be in order, so maintain first and last
              // position.
              if (firstPosition.obfuscatedLine > mp.obfuscatedLine) {
                firstPosition = mp;
              }
              if (lastPosition.obfuscatedLine < mp.obfuscatedLine) {
                lastPosition = mp;
              }
            }
            Range obfuscatedRange =
                new Range(firstPosition.obfuscatedLine, lastPosition.obfuscatedLine);
            Range originalRange = new Range(firstPosition.originalLine, lastPosition.originalLine);

            ClassNaming.Builder classNamingBuilder = onDemandClassNamingBuilder.get();
            classNamingBuilder.addMappedRange(
                obfuscatedRange,
                getOriginalMethodSignature.apply(firstPosition.method),
                originalRange,
                obfuscatedName);
            Position caller = firstPosition.caller;
            while (caller != null) {
              classNamingBuilder.addMappedRange(
                  obfuscatedRange,
                  getOriginalMethodSignature.apply(caller.method),
                  Math.max(caller.line, 0), // Prevent against "no-position".
                  obfuscatedName);
              caller = caller.callerPosition;
            }
            i = j;
          }
        } // for each method of the group
      } // for each method group, grouped by name
    } // for each class
    return classNameMapperBuilder.build();
  }

  private static boolean verifyMethodsAreKeptDirectlyOrIndirectly(
      AppView<AppInfoWithClassHierarchy> appView, List<DexEncodedMethod> methods) {
    if (appView.options().isGeneratingClassFiles()) {
      return true;
    }
    KeepInfoCollection keepInfo = appView.getKeepInfo();
    boolean allSeenAreInstanceInitializers = true;
    DexString originalName = null;
    for (DexEncodedMethod method : methods) {
      // We cannot rename instance initializers.
      if (method.isInstanceInitializer()) {
        assert allSeenAreInstanceInitializers;
        continue;
      }
      allSeenAreInstanceInitializers = false;
      // If the method is pinned, we cannot minify it.
      if (!keepInfo.isMinificationAllowed(method.method, appView, appView.options())) {
        continue;
      }
      // With desugared library, call-backs names are reserved here.
      if (method.isLibraryMethodOverride().isTrue()) {
        continue;
      }
      // We use the same name for interface names even if it has different types.
      DexProgramClass clazz = appView.definitionForProgramType(method.holder());
      DexClassAndMethod lookupResult =
          appView.appInfo().lookupMaximallySpecificMethod(clazz, method.method);
      if (lookupResult == null) {
        // We cannot rename methods we cannot look up.
        continue;
      }
      String errorString = method.method.qualifiedName() + " is not kept but is overloaded";
      assert lookupResult.getHolder().isInterface() : errorString;
      // TODO(b/159113601): Reenable assert.
      assert true || originalName == null || originalName.equals(method.method.name) : errorString;
      originalName = method.method.name;
    }
    return true;
  }

  private static int getMethodStartLine(DexEncodedMethod method) {
    Code code = method.getCode();
    if (code == null) {
      return 0;
    }
    if (code.isDexCode()) {
      DexDebugInfo dexDebugInfo = code.asDexCode().getDebugInfo();
      return dexDebugInfo == null ? 0 : dexDebugInfo.startLine;
    } else if (code.isCfCode()) {
      List<CfInstruction> instructions = code.asCfCode().getInstructions();
      for (CfInstruction instruction : instructions) {
        if (!(instruction instanceof CfPosition)) {
          continue;
        }
        return ((CfPosition) instruction).getPosition().line;
      }
    }
    return 0;
  }

  // Sort by startline, then DexEncodedMethod.slowCompare.
  // Use startLine = 0 if no debuginfo.
  private static void sortMethods(List<DexEncodedMethod> methods) {
    methods.sort(
        (lhs, rhs) -> {
          int lhsStartLine = getMethodStartLine(lhs);
          int rhsStartLine = getMethodStartLine(rhs);
          int startLineDiff = lhsStartLine - rhsStartLine;
          if (startLineDiff != 0) return startLineDiff;
          return DexEncodedMethod.slowCompare(lhs, rhs);
        });
  }

  @SuppressWarnings("ReturnValueIgnored")
  private static void addClassToClassNaming(
      DexType originalType,
      DexString renamedClassName,
      Supplier<Builder> onDemandClassNamingBuilder) {
    // We do know we need to create a ClassNaming.Builder if the class itself had been renamed.
    if (originalType.descriptor != renamedClassName) {
      // Not using return value, it's registered in classNameMapperBuilder
      onDemandClassNamingBuilder.get();
    }
  }

  private static void addFieldsToClassNaming(
      GraphLens graphLens,
      NamingLens namingLens,
      DexProgramClass clazz,
      DexType originalType,
      Supplier<Builder> onDemandClassNamingBuilder) {
    clazz.forEachField(
        dexEncodedField -> {
          DexField dexField = dexEncodedField.field;
          DexField originalField = graphLens.getOriginalFieldSignature(dexField);
          DexString renamedName = namingLens.lookupName(dexField);
          if (renamedName != originalField.name || originalField.holder != originalType) {
            FieldSignature originalSignature =
                FieldSignature.fromDexField(originalField, originalField.holder != originalType);
            MemberNaming memberNaming = new MemberNaming(originalSignature, renamedName.toString());
            onDemandClassNamingBuilder.get().addMemberEntry(memberNaming);
          }
        });
  }

  private static IdentityHashMap<DexString, List<DexEncodedMethod>> groupMethodsByRenamedName(
      GraphLens graphLens, NamingLens namingLens, DexProgramClass clazz) {
    IdentityHashMap<DexString, List<DexEncodedMethod>> methodsByRenamedName =
        new IdentityHashMap<>(clazz.getMethodCollection().size());
    for (DexEncodedMethod encodedMethod : clazz.methods()) {
      // Add method only if renamed, moved, or contains positions.
      DexMethod method = encodedMethod.method;
      DexString renamedName = namingLens.lookupName(method);
      if (renamedName != method.name
          || graphLens.getOriginalMethodSignature(method) != method
          || doesContainPositions(encodedMethod)) {
        methodsByRenamedName
            .computeIfAbsent(renamedName, key -> new ArrayList<>())
            .add(encodedMethod);
      }
    }
    return methodsByRenamedName;
  }

  private static boolean doesContainPositions(DexEncodedMethod method) {
    Code code = method.getCode();
    if (code == null) {
      return false;
    }
    if (code.isDexCode()) {
      return doesContainPositions(code.asDexCode());
    } else if (code.isCfCode()) {
      return doesContainPositions(code.asCfCode());
    }
    return false;
  }

  private static boolean doesContainPositions(DexCode dexCode) {
    DexDebugInfo debugInfo = dexCode.getDebugInfo();
    if (debugInfo == null) {
      return false;
    }
    for (DexDebugEvent event : debugInfo.events) {
      if (event instanceof DexDebugEvent.Default) {
        return true;
      }
    }
    return false;
  }

  private static boolean doesContainPositions(CfCode cfCode) {
    List<CfInstruction> instructions = cfCode.getInstructions();
    for (CfInstruction instruction : instructions) {
      if (instruction instanceof CfPosition) {
        return true;
      }
    }
    return false;
  }

  private static void optimizeDexCodePositions(
      DexEncodedMethod method,
      AppView<?> appView,
      PositionRemapper positionRemapper,
      List<MappedPosition> mappedPositions,
      boolean identityMapping) {
    // Do the actual processing for each method.
    final DexApplication application = appView.appInfo().app();
    DexCode dexCode = method.getCode().asDexCode();
    DexDebugInfo debugInfo = dexCode.getDebugInfo();
    List<DexDebugEvent> processedEvents = new ArrayList<>();

    PositionEventEmitter positionEventEmitter =
        new PositionEventEmitter(
            application.dexItemFactory,
            appView.graphLens().getOriginalMethodSignature(method.method),
            processedEvents);

    Box<Boolean> inlinedOriginalPosition = new Box<>(false);

    // Debug event visitor to map line numbers.
    DexDebugEventVisitor visitor =
        new DexDebugPositionState(
            debugInfo.startLine, appView.graphLens().getOriginalMethodSignature(method.method)) {

          // Keep track of what PC has been emitted.
          private int emittedPc = 0;

          // Force the current PC to emitted.
          private void flushPc() {
            if (emittedPc != getCurrentPc()) {
              positionEventEmitter.emitAdvancePc(getCurrentPc());
              emittedPc = getCurrentPc();
            }
          }

          // A default event denotes a line table entry and must always be emitted. Remap its line.
          @Override
          public void visit(Default defaultEvent) {
            super.visit(defaultEvent);
            assert getCurrentLine() >= 0;
            Position position =
                new Position(
                    getCurrentLine(),
                    getCurrentFile(),
                    getCurrentMethod(),
                    getCurrentCallerPosition());
            Position currentPosition = remapAndAdd(position, positionRemapper, mappedPositions);
            positionEventEmitter.emitPositionEvents(getCurrentPc(), currentPosition);
            if (currentPosition != position) {
              inlinedOriginalPosition.set(true);
            }
            emittedPc = getCurrentPc();
          }

          // Non-materializing events use super, ie, AdvancePC, AdvanceLine and SetInlineFrame.

          // Materializing events are just amended to the stream.

          @Override
          public void visit(SetFile setFile) {
            processedEvents.add(setFile);
          }

          @Override
          public void visit(SetPrologueEnd setPrologueEnd) {
            processedEvents.add(setPrologueEnd);
          }

          @Override
          public void visit(SetEpilogueBegin setEpilogueBegin) {
            processedEvents.add(setEpilogueBegin);
          }

          // Local changes must force flush the PC ensuing they pertain to the correct point.

          @Override
          public void visit(StartLocal startLocal) {
            flushPc();
            processedEvents.add(startLocal);
          }

          @Override
          public void visit(EndLocal endLocal) {
            flushPc();
            processedEvents.add(endLocal);
          }

          @Override
          public void visit(RestartLocal restartLocal) {
            flushPc();
            processedEvents.add(restartLocal);
          }
        };

    for (DexDebugEvent event : debugInfo.events) {
      event.accept(visitor);
    }

    DexDebugInfo optimizedDebugInfo =
        new DexDebugInfo(
            positionEventEmitter.getStartLine(),
            debugInfo.parameters,
            processedEvents.toArray(DexDebugEvent.EMPTY_ARRAY));

    assert !identityMapping
        || inlinedOriginalPosition.get()
        || verifyIdentityMapping(debugInfo, optimizedDebugInfo);

    dexCode.setDebugInfo(optimizedDebugInfo);
  }

  private static void optimizeDexCodePositionsForPc(
      DexEncodedMethod method,
      PositionRemapper positionRemapper,
      List<MappedPosition> mappedPositions) {
    // Do the actual processing for each method.
    DexCode dexCode = method.getCode().asDexCode();
    DexDebugInfo debugInfo = dexCode.getDebugInfo();

    Pair<Integer, Position> lastPosition = new Pair<>();

    DexDebugEventVisitor visitor =
        new DexDebugPositionState(debugInfo.startLine, method.method) {
          @Override
          public void visit(Default defaultEvent) {
            super.visit(defaultEvent);
            assert getCurrentLine() >= 0;
            if (lastPosition.getSecond() != null) {
              remapAndAddForPc(
                  lastPosition.getFirst(),
                  getCurrentPc(),
                  lastPosition.getSecond(),
                  positionRemapper,
                  mappedPositions);
            }
            lastPosition.setFirst(getCurrentPc());
            lastPosition.setSecond(
                new Position(
                    getCurrentLine(),
                    getCurrentFile(),
                    getCurrentMethod(),
                    getCurrentCallerPosition()));
          }
        };

    for (DexDebugEvent event : debugInfo.events) {
      event.accept(visitor);
    }

    if (lastPosition.getSecond() != null) {
      int lastPc = dexCode.instructions[dexCode.instructions.length - 1].getOffset();
      remapAndAddForPc(
          lastPosition.getFirst(),
          lastPc + 1,
          lastPosition.getSecond(),
          positionRemapper,
          mappedPositions);
    }

    dexCode.setDebugInfo(null);
  }

  private static boolean verifyIdentityMapping(
      DexDebugInfo originalDebugInfo, DexDebugInfo optimizedDebugInfo) {
    assert optimizedDebugInfo.startLine == originalDebugInfo.startLine;
    assert optimizedDebugInfo.events.length == originalDebugInfo.events.length;
    for (int i = 0; i < originalDebugInfo.events.length; ++i) {
      assert optimizedDebugInfo.events[i].equals(originalDebugInfo.events[i]);
    }
    return true;
  }

  private static void optimizeCfCodePositions(
      DexEncodedMethod method,
      PositionRemapper positionRemapper,
      List<MappedPosition> mappedPositions,
      AppView<?> appView) {
    // Do the actual processing for each method.
    CfCode oldCode = method.getCode().asCfCode();
    List<CfInstruction> oldInstructions = oldCode.getInstructions();
    List<CfInstruction> newInstructions = new ArrayList<>(oldInstructions.size());
    for (CfInstruction oldInstruction : oldInstructions) {
      CfInstruction newInstruction;
      if (oldInstruction instanceof CfPosition) {
        CfPosition cfPosition = (CfPosition) oldInstruction;
        newInstruction =
            new CfPosition(
                cfPosition.getLabel(),
                remapAndAdd(cfPosition.getPosition(), positionRemapper, mappedPositions));
      } else {
        newInstruction = oldInstruction;
      }
      newInstructions.add(newInstruction);
    }
    method.setCode(
        new CfCode(
            method.holder(),
            oldCode.getMaxStack(),
            oldCode.getMaxLocals(),
            newInstructions,
            oldCode.getTryCatchRanges(),
            oldCode.getLocalVariables()),
        appView);
  }

  private static Position remapAndAdd(
      Position position, PositionRemapper remapper, List<MappedPosition> mappedPositions) {
    Pair<Position, Position> remappedPosition = remapper.createRemappedPosition(position);
    Position oldPosition = remappedPosition.getFirst();
    Position newPosition = remappedPosition.getSecond();
    mappedPositions.add(
        new MappedPosition(
            oldPosition.method, oldPosition.line, oldPosition.callerPosition, newPosition.line));
    return newPosition;
  }

  private static void remapAndAddForPc(
      int startPc,
      int endPc,
      Position position,
      PositionRemapper remapper,
      List<MappedPosition> mappedPositions) {
    Pair<Position, Position> remappedPosition = remapper.createRemappedPosition(position);
    Position oldPosition = remappedPosition.getFirst();
    for (int currentPc = startPc; currentPc < endPc; currentPc++) {
      mappedPositions.add(
          new MappedPosition(
              oldPosition.method, oldPosition.line, oldPosition.callerPosition, currentPc));
    }
  }
}
