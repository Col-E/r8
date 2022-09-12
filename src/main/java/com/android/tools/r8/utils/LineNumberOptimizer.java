// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.ResourceException;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfPosition;
import com.android.tools.r8.debuginfo.DebugRepresentation;
import com.android.tools.r8.debuginfo.DebugRepresentation.DebugRepresentationPredicate;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.errors.Unreachable;
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
import com.android.tools.r8.graph.DexDebugInfo.EventBasedDebugInfo;
import com.android.tools.r8.graph.DexDebugInfoForSingleLineMethod;
import com.android.tools.r8.graph.DexDebugPositionState;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue.DexValueString;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Position.OutlineCallerPosition;
import com.android.tools.r8.ir.code.Position.OutlineCallerPosition.OutlineCallerPositionBuilder;
import com.android.tools.r8.ir.code.Position.OutlinePosition;
import com.android.tools.r8.ir.code.Position.PositionBuilder;
import com.android.tools.r8.ir.code.Position.SourcePosition;
import com.android.tools.r8.kotlin.KotlinSourceDebugExtensionParser;
import com.android.tools.r8.kotlin.KotlinSourceDebugExtensionParser.Result;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.naming.ClassNaming;
import com.android.tools.r8.naming.ClassNaming.Builder;
import com.android.tools.r8.naming.ClassNamingForNameMapper.MappedRange;
import com.android.tools.r8.naming.MapVersion;
import com.android.tools.r8.naming.MemberNaming;
import com.android.tools.r8.naming.MemberNaming.FieldSignature;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.naming.PositionRangeAllocator;
import com.android.tools.r8.naming.PositionRangeAllocator.CardinalPositionRangeAllocator;
import com.android.tools.r8.naming.PositionRangeAllocator.NonCardinalPositionRangeAllocator;
import com.android.tools.r8.naming.ProguardMapSupplier;
import com.android.tools.r8.naming.ProguardMapSupplier.ProguardMapId;
import com.android.tools.r8.naming.Range;
import com.android.tools.r8.naming.mappinginformation.CompilerSynthesizedMappingInformation;
import com.android.tools.r8.naming.mappinginformation.FileNameInformation;
import com.android.tools.r8.naming.mappinginformation.MappingInformation;
import com.android.tools.r8.naming.mappinginformation.OutlineCallsiteMappingInformation;
import com.android.tools.r8.naming.mappinginformation.OutlineMappingInformation;
import com.android.tools.r8.naming.mappinginformation.ResidualSignatureMappingInformation.ResidualMethodSignatureMappingInformation;
import com.android.tools.r8.naming.mappinginformation.RewriteFrameMappingInformation;
import com.android.tools.r8.naming.mappinginformation.RewriteFrameMappingInformation.RemoveInnerFramesAction;
import com.android.tools.r8.naming.mappinginformation.RewriteFrameMappingInformation.ThrowsCondition;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.retrace.internal.RetraceUtils;
import com.android.tools.r8.shaking.KeepInfoCollection;
import com.android.tools.r8.utils.InternalOptions.LineNumberOptimization;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntSortedMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.Function;

public class LineNumberOptimizer {

  private static final int MAX_LINE_NUMBER = 65535;

  // PositionRemapper is a stateful function which takes a position (represented by a
  // DexDebugPositionState) and returns a remapped Position.
  private interface PositionRemapper {
    Pair<Position, Position> createRemappedPosition(Position position);
  }

  private static class IdentityPositionRemapper implements PositionRemapper {

    @Override
    public Pair<Position, Position> createRemappedPosition(Position position) {
      // If we create outline calls we have to map them.
      assert position.getOutlineCallee() == null;
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
      assert position.getMethod() != null;
      if (previousMethod == position.getMethod()) {
        assert previousSourceLine >= 0;
        if (position.getLine() > previousSourceLine
            && position.getLine() - previousSourceLine <= maxLineDelta) {
          nextOptimizedLineNumber += (position.getLine() - previousSourceLine) - 1;
        }
      }

      Position newPosition =
          position
              .builderWithCopy()
              .setLine(nextOptimizedLineNumber++)
              .setCallerPosition(null)
              .build();
      previousSourceLine = position.getLine();
      previousMethod = position.getMethod();
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
      int line = position.getLine();
      Result parsedData = getAndParseSourceDebugExtension(position.getMethod().holder);
      if (parsedData == null) {
        return baseRemapper.createRemappedPosition(position);
      }
      Map.Entry<Integer, KotlinSourceDebugExtensionParser.Position> inlinedPosition =
          parsedData.lookupInlinedPosition(line);
      if (inlinedPosition == null) {
        return baseRemapper.createRemappedPosition(position);
      }
      int inlineeLineDelta = line - inlinedPosition.getKey();
      int originalInlineeLine = inlinedPosition.getValue().getRange().from + inlineeLineDelta;
      try {
        String binaryName = inlinedPosition.getValue().getSource().getPath();
        String nameAndDescriptor =
            lineToMethodMapper.lookupNameAndDescriptor(binaryName, originalInlineeLine);
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
        if (!inlinee.equals(position.getMethod())) {
          // We have an inline from a different method than the current position.
          Entry<Integer, KotlinSourceDebugExtensionParser.Position> calleePosition =
              parsedData.lookupCalleePosition(line);
          if (calleePosition != null) {
            // Take the first line as the callee position
            position =
                position
                    .builderWithCopy()
                    .setLine(calleePosition.getValue().getRange().from)
                    .build();
          }
          return baseRemapper.createRemappedPosition(
              SourcePosition.builder()
                  .setLine(originalInlineeLine)
                  .setMethod(inlinee)
                  .setCallerPosition(position)
                  .build());
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
      DexClass clazz = appView.definitionFor(currentMethod.getHolderType());
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
        startLine = currentPosition.getLine();
        previousPosition = SourcePosition.builder().setLine(startLine).setMethod(method).build();
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
    private final boolean isOutline;
    private final DexMethod outlineCallee;
    private final Int2StructuralItemArrayMap<Position> outlinePositions;

    private MappedPosition(
        DexMethod method,
        int originalLine,
        Position caller,
        int obfuscatedLine,
        boolean isOutline,
        DexMethod outlineCallee,
        Int2StructuralItemArrayMap<Position> outlinePositions) {
      this.method = method;
      this.originalLine = originalLine;
      this.caller = caller;
      this.obfuscatedLine = obfuscatedLine;
      this.isOutline = isOutline;
      this.outlineCallee = outlineCallee;
      this.outlinePositions = outlinePositions;
    }

    public boolean isOutlineCaller() {
      return outlineCallee != null;
    }
  }

  public static ProguardMapId runAndWriteMap(
      AndroidApp inputApp,
      AppView<?> appView,
      Timing timing,
      OriginalSourceFiles originalSourceFiles,
      DebugRepresentationPredicate representation) {
    assert appView.options().proguardMapConsumer != null;
    // When line number optimization is turned off the identity mapping for line numbers is
    // used. We still run the line number optimizer to collect line numbers and inline frame
    // information for the mapping file.
    timing.begin("Line number remapping");
    ClassNameMapper mapper =
        run(
            appView,
            inputApp,
            originalSourceFiles,
            representation);
    timing.end();
    timing.begin("Write proguard map");
    ProguardMapId mapId = ProguardMapSupplier.create(mapper, appView.options()).writeProguardMap();
    timing.end();
    return mapId;
  }

  private interface PcBasedDebugInfoRecorder {
    /** Callback to record a code object with a given max instruction PC and parameter count. */
    void recordPcMappingFor(ProgramMethod method, int maxEncodingPc);

    /** Callback to record a code object with only a single "line". */
    void recordSingleLineFor(ProgramMethod method, int maxEncodingPc);

    /**
     * Install the correct debug info objects.
     *
     * <p>Must be called after all recordings have been given to allow computing the debug info
     * items to be installed.
     */
    void updateDebugInfoInCodeObjects();

    int getPcEncoding(int pc);
  }

  private static class Pc2PcMappingSupport implements PcBasedDebugInfoRecorder {

    private static class UpdateInfo {
      final DexCode code;
      final int paramCount;
      final int maxEncodingPc;

      public UpdateInfo(DexCode code, int paramCount, int maxEncodingPc) {
        this.code = code;
        this.paramCount = paramCount;
        this.maxEncodingPc = maxEncodingPc;
      }

      // Used as key when building the shared debug info map.
      // Only param and max-pc are part of the key.

      @Override
      public boolean equals(Object o) {
        UpdateInfo that = (UpdateInfo) o;
        return paramCount == that.paramCount && maxEncodingPc == that.maxEncodingPc;
      }

      @Override
      public int hashCode() {
        return Objects.hash(paramCount, maxEncodingPc);
      }
    }

    private final List<UpdateInfo> codesToUpdate = new ArrayList<>();

    // We can only drop single-line debug info if it is OK to lose the source-file info.
    // This list is null if we must retain single-line entries.
    private final List<DexCode> singleLineCodesToClear;

    public Pc2PcMappingSupport(boolean allowDiscardingSourceFile) {
      singleLineCodesToClear = allowDiscardingSourceFile ? new ArrayList<>() : null;
    }

    @Override
    public int getPcEncoding(int pc) {
      assert pc >= 0;
      return pc + 1;
    }

    private boolean cantAddToClearSet(ProgramMethod method) {
      assert method.getDefinition().getCode().isDexCode();
      if (singleLineCodesToClear == null) {
        return true;
      }
      singleLineCodesToClear.add(method.getDefinition().getCode().asDexCode());
      return false;
    }

    @Override
    public void recordPcMappingFor(ProgramMethod method, int maxEncodingPc) {
      assert method.getDefinition().getCode().isDexCode();
      int parameterCount = method.getParameters().size();
      DexCode code = method.getDefinition().getCode().asDexCode();
      assert DebugRepresentation.verifyLastExecutableInstructionWithinBound(code, maxEncodingPc);
      codesToUpdate.add(new UpdateInfo(code, parameterCount, maxEncodingPc));
    }

    @Override
    public void recordSingleLineFor(ProgramMethod method, int maxEncodingPc) {
      if (cantAddToClearSet(method)) {
        recordPcMappingFor(method, maxEncodingPc);
      }
    }

    @Override
    public void updateDebugInfoInCodeObjects() {
      Map<UpdateInfo, DexDebugInfo> debugInfos = new HashMap<>();
      codesToUpdate.forEach(
          entry -> {
            assert DebugRepresentation.verifyLastExecutableInstructionWithinBound(
                entry.code, entry.maxEncodingPc);
            DexDebugInfo debugInfo =
                debugInfos.computeIfAbsent(entry, Pc2PcMappingSupport::buildPc2PcDebugInfo);
            assert debugInfo.asPcBasedInfo().getMaxPc() == entry.maxEncodingPc;
            entry.code.setDebugInfo(debugInfo);
          });
      if (singleLineCodesToClear != null) {
        singleLineCodesToClear.forEach(c -> c.setDebugInfo(null));
      }
    }

    private static DexDebugInfo buildPc2PcDebugInfo(UpdateInfo info) {
      return new DexDebugInfo.PcBasedDebugInfo(info.paramCount, info.maxEncodingPc);
    }
  }

  private static class NativePcSupport implements PcBasedDebugInfoRecorder {

    @Override
    public int getPcEncoding(int pc) {
      assert pc >= 0;
      return pc;
    }

    private void clearDebugInfo(ProgramMethod method) {
      // Always strip the info in full as the runtime will emit the PC directly.
      method.getDefinition().getCode().asDexCode().setDebugInfo(null);
    }

    @Override
    public void recordPcMappingFor(ProgramMethod method, int maxEncodingPc) {
      clearDebugInfo(method);
    }

    @Override
    public void recordSingleLineFor(ProgramMethod method, int maxEncodingPc) {
      clearDebugInfo(method);
    }

    @Override
    public void updateDebugInfoInCodeObjects() {
      // Already null out the info so nothing to do.
    }
  }

  public static ClassNameMapper run(
      AppView<?> appView,
      AndroidApp inputApp,
      OriginalSourceFiles originalSourceFiles,
      DebugRepresentationPredicate representation) {
    // For finding methods in kotlin files based on SourceDebugExtensions, we use a line method map.
    // We create it here to ensure it is only reading class files once.
    // TODO(b/220999985): Make this threaded per virtual file. Possibly pull the kotlin line mapping
    //  onto main thread before threading.
    CfLineToMethodMapper cfLineToMethodMapper = new CfLineToMethodMapper(inputApp);
    ClassNameMapper.Builder classNameMapperBuilder = ClassNameMapper.builder();

    Map<DexMethod, OutlineFixupBuilder> outlinesToFix = new IdentityHashMap<>();
    Map<DexType, String> prunedInlinedClasses = new IdentityHashMap<>();

    PcBasedDebugInfoRecorder pcBasedDebugInfo =
        appView.options().canUseNativeDexPcInsteadOfDebugInfo()
            ? new NativePcSupport()
            : new Pc2PcMappingSupport(appView.options().allowDiscardingResidualDebugInfo());

    CardinalPositionRangeAllocator cardinalRangeCache =
        PositionRangeAllocator.createCardinalPositionRangeAllocator();
    NonCardinalPositionRangeAllocator nonCardinalRangeCache =
        PositionRangeAllocator.createNonCardinalPositionRangeAllocator();

    // Collect which files contain which classes that need to have their line numbers optimized.
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      boolean isSyntheticClass = appView.getSyntheticItems().isSyntheticClass(clazz);

      IdentityHashMap<DexString, List<ProgramMethod>> methodsByRenamedName =
          groupMethodsByRenamedName(appView, clazz);

      // At this point we don't know if we really need to add this class to the builder.
      // It depends on whether any methods/fields are renamed or some methods contain positions.
      // Create a supplier which creates a new, cached ClassNaming.Builder on-demand.
      DexType originalType = appView.graphLens().getOriginalType(clazz.type);
      DexString renamedDescriptor = appView.getNamingLens().lookupDescriptor(clazz.getType());
      LazyBox<ClassNaming.Builder> onDemandClassNamingBuilder =
          new LazyBox<>(
              () ->
                  classNameMapperBuilder.classNamingBuilder(
                      DescriptorUtils.descriptorToJavaType(renamedDescriptor.toString()),
                      originalType.toSourceString(),
                      com.android.tools.r8.position.Position.UNKNOWN));

      // Check if source file should be added to the map
      DexString originalSourceFile = originalSourceFiles.getOriginalSourceFile(clazz);
      if (originalSourceFile != null) {
        String sourceFile = originalSourceFile.toString();
        if (!RetraceUtils.hasPredictableSourceFileName(clazz.toSourceString(), sourceFile)) {
          onDemandClassNamingBuilder
              .computeIfAbsent()
              .addMappingInformation(FileNameInformation.build(sourceFile), Unreachable::raise);
        }
      }

      if (isSyntheticClass) {
        onDemandClassNamingBuilder
            .computeIfAbsent()
            .addMappingInformation(
                CompilerSynthesizedMappingInformation.builder().build(), Unreachable::raise);
      }

      // If the class is renamed add it to the classNamingBuilder.
      addClassToClassNaming(originalType, renamedDescriptor, onDemandClassNamingBuilder);

      // First transfer renamed fields to classNamingBuilder.
      addFieldsToClassNaming(appView, clazz, originalType, onDemandClassNamingBuilder);

      // Then process the methods, ordered by renamed name.
      List<DexString> renamedMethodNames = new ArrayList<>(methodsByRenamedName.keySet());
      renamedMethodNames.sort(DexString::compareTo);
      for (DexString methodName : renamedMethodNames) {
        List<ProgramMethod> methods = methodsByRenamedName.get(methodName);
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

        for (ProgramMethod method : methods) {
          DexEncodedMethod definition = method.getDefinition();
          kotlinRemapper.currentMethod = definition;
          List<MappedPosition> mappedPositions;
          Code code = definition.getCode();
          int pcEncodingCutoff =
              methods.size() == 1 ? representation.getDexPcEncodingCutoff(method) : -1;
          boolean canUseDexPc = pcEncodingCutoff > 0;
          if (code != null) {
            if (code.isDexCode()
                && mustHaveResidualDebugInfo(code.asDexCode(), appView.options())) {
              if (canUseDexPc) {
                mappedPositions =
                    optimizeDexCodePositionsForPc(
                        method, pcEncodingCutoff, appView, kotlinRemapper, pcBasedDebugInfo);
              } else {
                mappedPositions =
                    optimizeDexCodePositions(
                        definition, appView, kotlinRemapper, identityMapping, methods.size() != 1);
              }
            } else if (code.isCfCode()
                && mustHaveResidualDebugInfo(code.asCfCode())
                && !appView.isCfByteCodePassThrough(definition)) {
              mappedPositions = optimizeCfCodePositions(method, kotlinRemapper, appView);
            } else {
              mappedPositions = new ArrayList<>();
            }
          } else {
            mappedPositions = new ArrayList<>();
          }

          DexMethod originalMethod =
              appView.graphLens().getOriginalMethodSignature(method.getReference());
          MethodSignature originalSignature =
              MethodSignature.fromDexMethod(originalMethod, originalMethod.holder != originalType);

          DexString obfuscatedNameDexString =
              appView.getNamingLens().lookupName(method.getReference());
          String obfuscatedName = obfuscatedNameDexString.toString();

          List<MappingInformation> methodMappingInfo = new ArrayList<>();
          if (definition.isD8R8Synthesized()) {
            methodMappingInfo.add(CompilerSynthesizedMappingInformation.builder().build());
          }

          MapVersion mapFileVersion = appView.options().getMapFileVersion();
          if (isIdentityMapping(
              mapFileVersion,
              mappedPositions,
              methodMappingInfo,
              method,
              obfuscatedNameDexString,
              originalMethod,
              originalType)) {
            assert appView.options().lineNumberOptimization == LineNumberOptimization.OFF
                || hasAtMostOnePosition(definition, appView.options())
                || appView.isCfByteCodePassThrough(definition);
            continue;
          }
          // TODO(b/169953605): Ensure we emit the residual signature information.
          if (mapFileVersion.isGreaterThan(MapVersion.MAP_VERSION_2_1)
              && originalMethod != method.getReference()) {
            methodMappingInfo.add(
                ResidualMethodSignatureMappingInformation.fromDexMethod(method.getReference()));
          }

          MemberNaming memberNaming = new MemberNaming(originalSignature, obfuscatedName);
          onDemandClassNamingBuilder.computeIfAbsent().addMemberEntry(memberNaming);

          // Add simple "a() -> b" mapping if we won't have any other with concrete line numbers
          if (mappedPositions.isEmpty()) {
            MappedRange range =
                onDemandClassNamingBuilder
                    .computeIfAbsent()
                    .addMappedRange(null, originalSignature, null, obfuscatedName);
            methodMappingInfo.forEach(
                info -> range.addMappingInformation(info, Unreachable::raise));
            continue;
          }

          Map<DexMethod, MethodSignature> signatures = new IdentityHashMap<>();
          signatures.put(originalMethod, originalSignature);
          Function<DexMethod, MethodSignature> getOriginalMethodSignature =
              m ->
                  signatures.computeIfAbsent(
                      m, key -> MethodSignature.fromDexMethod(m, m.holder != clazz.getType()));

          // Check if mapped position is an outline
          DexMethod outlineMethod = getOutlineMethod(mappedPositions.get(0));
          if (outlineMethod != null) {
            outlinesToFix
                .computeIfAbsent(
                    outlineMethod,
                    outline -> new OutlineFixupBuilder(computeMappedMethod(outline, appView)))
                .setMappedPositionsOutline(mappedPositions);
            methodMappingInfo.add(OutlineMappingInformation.builder().build());
          }

          // Update memberNaming with the collected positions, merging multiple positions into a
          // single region whenever possible.
          for (int i = 0; i < mappedPositions.size(); /* updated in body */ ) {
            MappedPosition firstPosition = mappedPositions.get(i);
            int j = i + 1;
            MappedPosition lastPosition = firstPosition;
            MappedPositionRange mappedPositionRange = MappedPositionRange.SINGLE_LINE;
            for (; j < mappedPositions.size(); j++) {
              // Break if this position cannot be merged with lastPosition.
              MappedPosition currentPosition = mappedPositions.get(j);
              mappedPositionRange =
                  mappedPositionRange.canAddNextMappingToRange(lastPosition, currentPosition);
              // Note that currentPosition.caller and lastPosition.class must be deep-compared since
              // multiple inlining passes lose the canonical property of the positions.
              if (currentPosition.method != lastPosition.method
                  || mappedPositionRange.isOutOfRange()
                  || !Objects.equals(currentPosition.caller, lastPosition.caller)
                  // Break when we see a mapped outline
                  || currentPosition.outlineCallee != null
                  // Ensure that we break when we start iterating with an outline caller again.
                  || firstPosition.outlineCallee != null) {
                break;
              }
              // The mapped positions are not guaranteed to be in order, so maintain first and last
              // position.
              if (firstPosition.obfuscatedLine > currentPosition.obfuscatedLine) {
                firstPosition = currentPosition;
              }
              if (lastPosition.obfuscatedLine < currentPosition.obfuscatedLine) {
                lastPosition = currentPosition;
              }
            }
            Range obfuscatedRange;
            if (definition.getCode().isDexCode()
                && definition.getCode().asDexCode().getDebugInfo()
                    == DexDebugInfoForSingleLineMethod.getInstance()) {
              assert firstPosition.originalLine == lastPosition.originalLine;
              obfuscatedRange = nonCardinalRangeCache.get(0, MAX_LINE_NUMBER);
            } else {
              obfuscatedRange =
                  nonCardinalRangeCache.get(
                      firstPosition.obfuscatedLine, lastPosition.obfuscatedLine);
            }
            ClassNaming.Builder classNamingBuilder = onDemandClassNamingBuilder.computeIfAbsent();
            MappedRange lastMappedRange =
                getMappedRangesForPosition(
                    appView,
                    getOriginalMethodSignature,
                    classNamingBuilder,
                    firstPosition.method,
                    obfuscatedName,
                    obfuscatedRange,
                    nonCardinalRangeCache.get(
                        firstPosition.originalLine, lastPosition.originalLine),
                    firstPosition.caller,
                    prunedInlinedClasses,
                    cardinalRangeCache);
            for (MappingInformation info : methodMappingInfo) {
              lastMappedRange.addMappingInformation(info, Unreachable::raise);
            }
            // firstPosition will contain a potential outline caller.
            if (firstPosition.outlineCallee != null) {
              Int2IntMap positionMap = new Int2IntArrayMap();
              int maxPc = ListUtils.last(mappedPositions).obfuscatedLine;
              firstPosition.outlinePositions.forEach(
                  (line, position) -> {
                    int placeHolderLineToBeFixed;
                    if (canUseDexPc) {
                      placeHolderLineToBeFixed = maxPc + line + 1;
                    } else {
                      placeHolderLineToBeFixed =
                          positionRemapper.createRemappedPosition(position).getSecond().getLine();
                    }
                    positionMap.put((int) line, placeHolderLineToBeFixed);
                    getMappedRangesForPosition(
                        appView,
                        getOriginalMethodSignature,
                        classNamingBuilder,
                        position.getMethod(),
                        obfuscatedName,
                        nonCardinalRangeCache.get(
                            placeHolderLineToBeFixed, placeHolderLineToBeFixed),
                        nonCardinalRangeCache.get(position.getLine(), position.getLine()),
                        position.getCallerPosition(),
                        prunedInlinedClasses,
                        cardinalRangeCache);
                  });
              outlinesToFix
                  .computeIfAbsent(
                      firstPosition.outlineCallee,
                      outline -> new OutlineFixupBuilder(computeMappedMethod(outline, appView)))
                  .addMappedRangeForOutlineCallee(lastMappedRange, positionMap);
            }
            i = j;
          }
          if (definition.getCode().isDexCode()
              && definition.getCode().asDexCode().getDebugInfo()
                  == DexDebugInfoForSingleLineMethod.getInstance()) {
            pcBasedDebugInfo.recordSingleLineFor(method, pcEncodingCutoff);
          }
        } // for each method of the group
      } // for each method group, grouped by name
    } // for each class

    // Fixup all outline positions
    outlinesToFix.values().forEach(OutlineFixupBuilder::fixup);

    // Update all the debug-info objects.
    pcBasedDebugInfo.updateDebugInfoInCodeObjects();

    // Add all pruned inline classes to the mapping to recover source files.
    List<Entry<DexType, String>> prunedEntries = new ArrayList<>(prunedInlinedClasses.entrySet());
    prunedEntries.sort(Entry.comparingByKey());
    prunedEntries.forEach(
        entry -> {
          DexType holder = entry.getKey();
          assert appView.appInfo().definitionForWithoutExistenceAssert(holder) == null;
          String typeName = holder.toSourceString();
          String sourceFile = entry.getValue();
          assert !RetraceUtils.hasPredictableSourceFileName(typeName, sourceFile);
          classNameMapperBuilder
              .classNamingBuilder(
                  typeName, typeName, com.android.tools.r8.position.Position.UNKNOWN)
              .addMappingInformation(FileNameInformation.build(sourceFile), Unreachable::raise);
        });

    return classNameMapperBuilder.build();
  }

  private static boolean isIdentityMapping(
      MapVersion mapFileVersion,
      List<MappedPosition> mappedPositions,
      List<MappingInformation> methodMappingInfo,
      ProgramMethod method,
      DexString obfuscatedNameDexString,
      DexMethod originalMethod,
      DexType originalType) {
    if (mapFileVersion.isGreaterThan(MapVersion.MAP_VERSION_2_1)) {
      // Don't emit pure identity mappings.
      return mappedPositions.isEmpty()
          && methodMappingInfo.isEmpty()
          && originalMethod == method.getReference();
    } else {
      // Don't emit pure identity mappings.
      return mappedPositions.isEmpty()
          && methodMappingInfo.isEmpty()
          && obfuscatedNameDexString == originalMethod.name
          && originalMethod.holder == originalType;
    }
  }

  private static boolean hasAtMostOnePosition(
      DexEncodedMethod definition, InternalOptions options) {
    if (!mustHaveResidualDebugInfo(definition, options)) {
      return true;
    }
    Code code = definition.getCode();
    if (code.isDexCode() && code.asDexCode().instructions.length == 1) {
      // If the dex code is a single PC code then that also qualifies as having at most one
      // position.
      return true;
    }
    return false;
  }

  private static MethodReference computeMappedMethod(DexMethod current, AppView<?> appView) {
    NamingLens namingLens = appView.getNamingLens();
    DexMethod renamedMethodSignature =
        namingLens.lookupMethod(
            appView.graphLens().getRenamedMethodSignature(current), appView.dexItemFactory());
    return renamedMethodSignature.asMethodReference();
  }

  private static DexMethod getOutlineMethod(MappedPosition mappedPosition) {
    if (mappedPosition.isOutline) {
      return mappedPosition.method;
    }
    if (mappedPosition.caller == null) {
      return null;
    }
    Position outermostCaller = mappedPosition.caller.getOutermostCaller();
    return outermostCaller.isOutline() ? outermostCaller.getMethod() : null;
  }

  private static MappedRange getMappedRangesForPosition(
      AppView<?> appView,
      Function<DexMethod, MethodSignature> getOriginalMethodSignature,
      Builder classNamingBuilder,
      DexMethod method,
      String obfuscatedName,
      Range obfuscatedRange,
      Range originalLine,
      Position caller,
      Map<DexType, String> prunedInlineHolder,
      CardinalPositionRangeAllocator cardinalRangeCache) {
    MappedRange lastMappedRange =
        classNamingBuilder.addMappedRange(
            obfuscatedRange,
            getOriginalMethodSignature.apply(method),
            originalLine,
            obfuscatedName);
    int inlineFramesCount = 0;
    while (caller != null) {
      inlineFramesCount += 1;
      String prunedClassSourceFileInfo =
          appView.getPrunedClassSourceFileInfo(method.getHolderType());
      if (prunedClassSourceFileInfo != null) {
        String originalValue =
            prunedInlineHolder.put(method.getHolderType(), prunedClassSourceFileInfo);
        assert originalValue == null || originalValue.equals(prunedClassSourceFileInfo);
      }
      lastMappedRange =
          classNamingBuilder.addMappedRange(
              obfuscatedRange,
              getOriginalMethodSignature.apply(caller.getMethod()),
              cardinalRangeCache.get(
                  Math.max(caller.getLine(), 0)), // Prevent against "no-position".
              obfuscatedName);
      if (caller.isRemoveInnerFramesIfThrowingNpe()) {
        lastMappedRange.addMappingInformation(
            RewriteFrameMappingInformation.builder()
                .addCondition(
                    ThrowsCondition.create(
                        Reference.classFromDescriptor(
                            appView.dexItemFactory().npeDescriptor.toString())))
                .addRewriteAction(RemoveInnerFramesAction.create(inlineFramesCount))
                .build(),
            Unreachable::raise);
      }
      caller = caller.getCallerPosition();
    }
    return lastMappedRange;
  }

  private static boolean verifyMethodsAreKeptDirectlyOrIndirectly(
      AppView<?> appView, List<ProgramMethod> methods) {
    if (appView.options().isGeneratingClassFiles() || !appView.appInfo().hasClassHierarchy()) {
      return true;
    }
    AppInfoWithClassHierarchy appInfo = appView.appInfo().withClassHierarchy();
    KeepInfoCollection keepInfo = appView.getKeepInfo();
    boolean allSeenAreInstanceInitializers = true;
    DexString originalName = null;
    for (ProgramMethod method : methods) {
      // We cannot rename instance initializers.
      if (method.getDefinition().isInstanceInitializer()) {
        assert allSeenAreInstanceInitializers;
        continue;
      }
      allSeenAreInstanceInitializers = false;
      // If the method is pinned, we cannot minify it.
      if (!keepInfo.isMinificationAllowed(method.getReference(), appView, appView.options())) {
        continue;
      }
      // With desugared library, call-backs names are reserved here.
      if (method.getDefinition().isLibraryMethodOverride().isTrue()) {
        continue;
      }
      // We use the same name for interface names even if it has different types.
      DexProgramClass clazz = appView.definitionForProgramType(method.getHolderType());
      DexClassAndMethod lookupResult =
          appInfo.lookupMaximallySpecificMethod(clazz, method.getReference());
      if (lookupResult == null) {
        // We cannot rename methods we cannot look up.
        continue;
      }
      String errorString = method.getReference().qualifiedName() + " is not kept but is overloaded";
      assert lookupResult.getHolder().isInterface() : errorString;
      // TODO(b/159113601): Reenable assert.
      assert true || originalName == null || originalName.equals(method.getReference().name)
          : errorString;
      originalName = method.getReference().name;
    }
    return true;
  }

  private static int getMethodStartLine(ProgramMethod method) {
    Code code = method.getDefinition().getCode();
    if (code == null) {
      return 0;
    }
    if (code.isDexCode()) {
      DexDebugInfo dexDebugInfo = code.asDexCode().getDebugInfo();
      return dexDebugInfo == null ? 0 : dexDebugInfo.getStartLine();
    } else if (code.isCfCode()) {
      List<CfInstruction> instructions = code.asCfCode().getInstructions();
      for (CfInstruction instruction : instructions) {
        if (!(instruction instanceof CfPosition)) {
          continue;
        }
        return ((CfPosition) instruction).getPosition().getLine();
      }
    }
    return 0;
  }

  // Sort by startline, then DexEncodedMethod.slowCompare.
  // Use startLine = 0 if no debuginfo.
  private static void sortMethods(List<ProgramMethod> methods) {
    methods.sort(
        (lhs, rhs) -> {
          int lhsStartLine = getMethodStartLine(lhs);
          int rhsStartLine = getMethodStartLine(rhs);
          int startLineDiff = lhsStartLine - rhsStartLine;
          if (startLineDiff != 0) return startLineDiff;
          return DexEncodedMethod.slowCompare(lhs.getDefinition(), rhs.getDefinition());
        });
  }

  @SuppressWarnings("ReturnValueIgnored")
  private static void addClassToClassNaming(
      DexType originalType,
      DexString renamedClassName,
      LazyBox<Builder> onDemandClassNamingBuilder) {
    // We do know we need to create a ClassNaming.Builder if the class itself had been renamed.
    if (originalType.descriptor != renamedClassName) {
      // Not using return value, it's registered in classNameMapperBuilder
      onDemandClassNamingBuilder.computeIfAbsent();
    }
  }

  private static void addFieldsToClassNaming(
      AppView<?> appView,
      DexProgramClass clazz,
      DexType originalType,
      LazyBox<Builder> onDemandClassNamingBuilder) {
    clazz.forEachField(
        dexEncodedField -> {
          DexField dexField = dexEncodedField.getReference();
          DexField originalField = appView.graphLens().getOriginalFieldSignature(dexField);
          DexString renamedName = appView.getNamingLens().lookupName(dexField);
          if (renamedName != originalField.name || originalField.holder != originalType) {
            FieldSignature originalSignature =
                FieldSignature.fromDexField(originalField, originalField.holder != originalType);
            MemberNaming memberNaming = new MemberNaming(originalSignature, renamedName.toString());
            onDemandClassNamingBuilder.computeIfAbsent().addMemberEntry(memberNaming);
          }
        });
  }

  public static IdentityHashMap<DexString, List<ProgramMethod>> groupMethodsByRenamedName(
      AppView<?> appView, DexProgramClass clazz) {
    IdentityHashMap<DexString, List<ProgramMethod>> methodsByRenamedName =
        new IdentityHashMap<>(clazz.getMethodCollection().size());
    for (ProgramMethod programMethod : clazz.programMethods()) {
      // Add method only if renamed, moved, or if it has debug info to map.
      DexEncodedMethod definition = programMethod.getDefinition();
      DexMethod method = programMethod.getReference();
      DexString renamedName = appView.getNamingLens().lookupName(method);
      if (renamedName != method.name
          || appView.graphLens().getOriginalMethodSignature(method) != method
          || mustHaveResidualDebugInfo(definition, appView.options())
          || definition.isD8R8Synthesized()) {
        methodsByRenamedName
            .computeIfAbsent(renamedName, key -> new ArrayList<>())
            .add(programMethod);
      }
    }
    return methodsByRenamedName;
  }

  private static boolean mustHaveResidualDebugInfo(
      DexEncodedMethod method, InternalOptions options) {
    Code code = method.getCode();
    if (code == null) {
      return false;
    }
    if (code.isDexCode()) {
      return mustHaveResidualDebugInfo(code.asDexCode(), options);
    } else if (code.isCfCode()) {
      return mustHaveResidualDebugInfo(code.asCfCode());
    }
    return false;
  }

  public static boolean mustHaveResidualDebugInfo(DexCode dexCode, InternalOptions options) {
    // All code objects must have debug info if discarding it is not allowed.
    if (!options.allowDiscardingResidualDebugInfo()) {
      return true;
    }
    // Otherwise debug info is only needed for code sequences with at least one position.
    DexDebugInfo debugInfo = dexCode.getDebugInfo();
    if (debugInfo == null) {
      return false;
    }
    if (debugInfo.isPcBasedInfo()) {
      return true;
    }
    for (DexDebugEvent event : debugInfo.asEventBasedInfo().events) {
      if (event instanceof DexDebugEvent.Default) {
        return true;
      }
    }
    return false;
  }

  private static boolean mustHaveResidualDebugInfo(CfCode cfCode) {
    List<CfInstruction> instructions = cfCode.getInstructions();
    for (CfInstruction instruction : instructions) {
      if (instruction instanceof CfPosition) {
        return true;
      }
    }
    return false;
  }

  private static List<MappedPosition> optimizeDexCodePositions(
      DexEncodedMethod method,
      AppView<?> appView,
      PositionRemapper positionRemapper,
      boolean identityMapping,
      boolean hasOverloads) {
    List<MappedPosition> mappedPositions = new ArrayList<>();
    // Do the actual processing for each method.
    DexApplication application = appView.appInfo().app();
    DexCode dexCode = method.getCode().asDexCode();
    EventBasedDebugInfo debugInfo = getEventBasedDebugInfo(method, dexCode, appView);

    List<DexDebugEvent> processedEvents = new ArrayList<>();

    PositionEventEmitter positionEventEmitter =
        new PositionEventEmitter(
            application.dexItemFactory,
            appView.graphLens().getOriginalMethodSignature(method.getReference()),
            processedEvents);

    Box<Boolean> inlinedOriginalPosition = new Box<>(false);

    // Debug event visitor to map line numbers.
    DexDebugPositionState visitor =
        new DexDebugPositionState(
            debugInfo.startLine,
            appView.graphLens().getOriginalMethodSignature(method.getReference())) {

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
            Position position = getPositionFromPositionState(this);
            Position currentPosition = remapAndAdd(position, positionRemapper, mappedPositions);
            positionEventEmitter.emitPositionEvents(getCurrentPc(), currentPosition);
            if (currentPosition != position) {
              inlinedOriginalPosition.set(true);
            }
            emittedPc = getCurrentPc();
            resetOutlineInformation();
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

    // If we only have one line event we can always retrace back uniquely.
    if (mappedPositions.size() <= 1
        && !hasOverloads
        && !appView.options().debug
        && appView.options().lineNumberOptimization != LineNumberOptimization.OFF
        && appView.options().allowDiscardingResidualDebugInfo()
        && (mappedPositions.isEmpty() || !mappedPositions.get(0).isOutlineCaller())) {
      dexCode.setDebugInfo(DexDebugInfoForSingleLineMethod.getInstance());
      return mappedPositions;
    }

    EventBasedDebugInfo optimizedDebugInfo =
        new EventBasedDebugInfo(
            positionEventEmitter.getStartLine(),
            debugInfo.parameters,
            processedEvents.toArray(DexDebugEvent.EMPTY_ARRAY));

    assert !identityMapping
        || inlinedOriginalPosition.get()
        || verifyIdentityMapping(debugInfo, optimizedDebugInfo);

    dexCode.setDebugInfo(optimizedDebugInfo);
    return mappedPositions;
  }

  // This conversion *always* creates an event based debug info encoding as any non-info will
  // be created as an implicit PC encoding.
  private static EventBasedDebugInfo getEventBasedDebugInfo(
      DexEncodedMethod method, DexCode dexCode, AppView<?> appView) {
    // TODO(b/213411850): Do we need to reconsider conversion here to support pc-based D8 merging?
    if (dexCode.getDebugInfo() == null) {
      return createEventBasedInfoForMethodWithoutDebugInfo(method, appView.dexItemFactory());
    }
    assert method.getParameters().size() == dexCode.getDebugInfo().getParameterCount();
    EventBasedDebugInfo debugInfo =
        DexDebugInfo.convertToEventBased(dexCode, appView.dexItemFactory());
    assert debugInfo != null;
    return debugInfo;
  }

  public static EventBasedDebugInfo createEventBasedInfoForMethodWithoutDebugInfo(
      DexEncodedMethod method, DexItemFactory factory) {
    return new EventBasedDebugInfo(
        0,
        new DexString[method.getParameters().size()],
        new DexDebugEvent[] {factory.zeroChangeDefaultEvent});
  }

  private static Position getPositionFromPositionState(DexDebugPositionState state) {
    PositionBuilder<?, ?> positionBuilder;
    if (state.getOutlineCallee() != null) {
      OutlineCallerPositionBuilder outlineCallerPositionBuilder =
          OutlineCallerPosition.builder()
              .setOutlineCallee(state.getOutlineCallee())
              .setIsOutline(state.isOutline());
      state.getOutlineCallerPositions().forEach(outlineCallerPositionBuilder::addOutlinePosition);
      positionBuilder = outlineCallerPositionBuilder;
    } else if (state.isOutline()) {
      positionBuilder = OutlinePosition.builder();
    } else {
      positionBuilder = SourcePosition.builder().setFile(state.getCurrentFile());
    }
    return positionBuilder
        .setLine(state.getCurrentLine())
        .setMethod(state.getCurrentMethod())
        .setCallerPosition(state.getCurrentCallerPosition())
        .build();
  }

  private static List<MappedPosition> optimizeDexCodePositionsForPc(
      ProgramMethod method,
      int pcEncodingCutoff,
      AppView<?> appView,
      PositionRemapper positionRemapper,
      PcBasedDebugInfoRecorder debugInfoProvider) {
    List<MappedPosition> mappedPositions = new ArrayList<>();
    // Do the actual processing for each method.
    DexCode dexCode = method.getDefinition().getCode().asDexCode();
    EventBasedDebugInfo debugInfo =
        getEventBasedDebugInfo(method.getDefinition(), dexCode, appView);
    IntBox firstDefaultEventPc = new IntBox(-1);
    BooleanBox singleOriginalLine = new BooleanBox(true);
    Pair<Integer, Position> lastPosition = new Pair<>();
    DexDebugEventVisitor visitor =
        new DexDebugPositionState(
            debugInfo.startLine,
            appView.graphLens().getOriginalMethodSignature(method.getReference())) {
          @Override
          public void visit(Default defaultEvent) {
            super.visit(defaultEvent);
            assert getCurrentLine() >= 0;
            if (firstDefaultEventPc.get() < 0) {
              firstDefaultEventPc.set(getCurrentPc());
            }
            Position currentPosition = getPositionFromPositionState(this);
            if (lastPosition.getSecond() != null) {
              if (singleOriginalLine.isTrue()
                  && !currentPosition.equals(lastPosition.getSecond())) {
                singleOriginalLine.set(false);
              }
              remapAndAddForPc(
                  debugInfoProvider,
                  lastPosition.getFirst(),
                  getCurrentPc(),
                  lastPosition.getSecond(),
                  positionRemapper,
                  mappedPositions);
            }
            lastPosition.setFirst(getCurrentPc());
            lastPosition.setSecond(currentPosition);
            resetOutlineInformation();
          }
        };

    for (DexDebugEvent event : debugInfo.events) {
      event.accept(visitor);
    }

    // If the method has a single non-preamble line, check that the preamble is not active on any
    // throwing instruction before the single line becomes active.
    if (singleOriginalLine.isTrue() && firstDefaultEventPc.get() > 0) {
      for (DexInstruction instruction : dexCode.instructions) {
        if (instruction.getOffset() < firstDefaultEventPc.get()) {
          if (instruction.canThrow()) {
            singleOriginalLine.set(false);
          }
        } else {
          break;
        }
      }
    }

    int lastInstructionPc = DebugRepresentation.getLastExecutableInstruction(dexCode).getOffset();
    if (lastPosition.getSecond() != null) {
      remapAndAddForPc(
          debugInfoProvider,
          lastPosition.getFirst(),
          lastInstructionPc + 1,
          lastPosition.getSecond(),
          positionRemapper,
          mappedPositions);
    }

    assert !mappedPositions.isEmpty() || dexCode.instructions.length == 1;
    if (singleOriginalLine.isTrue()
        && lastPosition.getSecond() != null
        && (mappedPositions.isEmpty() || !mappedPositions.get(0).isOutlineCaller())) {
      dexCode.setDebugInfo(DexDebugInfoForSingleLineMethod.getInstance());
      debugInfoProvider.recordSingleLineFor(method, pcEncodingCutoff);
    } else {
      debugInfoProvider.recordPcMappingFor(method, pcEncodingCutoff);
    }
    return mappedPositions;
  }

  private static boolean verifyIdentityMapping(
      EventBasedDebugInfo originalDebugInfo, EventBasedDebugInfo optimizedDebugInfo) {
    assert optimizedDebugInfo.startLine == originalDebugInfo.startLine;
    assert optimizedDebugInfo.events.length == originalDebugInfo.events.length;
    for (int i = 0; i < originalDebugInfo.events.length; ++i) {
      assert optimizedDebugInfo.events[i].equals(originalDebugInfo.events[i]);
    }
    return true;
  }

  private static List<MappedPosition> optimizeCfCodePositions(
      ProgramMethod method, PositionRemapper positionRemapper, AppView<?> appView) {
    List<MappedPosition> mappedPositions = new ArrayList<>();
    // Do the actual processing for each method.
    CfCode oldCode = method.getDefinition().getCode().asCfCode();
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
            method.getHolderType(),
            oldCode.getMaxStack(),
            oldCode.getMaxLocals(),
            newInstructions,
            oldCode.getTryCatchRanges(),
            oldCode.getLocalVariables()),
        appView);
    return mappedPositions;
  }

  private static Position remapAndAdd(
      Position position, PositionRemapper remapper, List<MappedPosition> mappedPositions) {
    Pair<Position, Position> remappedPosition = remapper.createRemappedPosition(position);
    Position oldPosition = remappedPosition.getFirst();
    Position newPosition = remappedPosition.getSecond();
    mappedPositions.add(
        new MappedPosition(
            oldPosition.getMethod(),
            oldPosition.getLine(),
            oldPosition.getCallerPosition(),
            newPosition.getLine(),
            oldPosition.isOutline(),
            oldPosition.getOutlineCallee(),
            oldPosition.getOutlinePositions()));
    return newPosition;
  }

  private static void remapAndAddForPc(
      PcBasedDebugInfoRecorder debugInfoProvider,
      int startPc,
      int endPc,
      Position position,
      PositionRemapper remapper,
      List<MappedPosition> mappedPositions) {
    Pair<Position, Position> remappedPosition = remapper.createRemappedPosition(position);
    Position oldPosition = remappedPosition.getFirst();
    for (int currentPc = startPc; currentPc < endPc; currentPc++) {
      boolean firstEntry = currentPc == startPc;
      mappedPositions.add(
          new MappedPosition(
              oldPosition.getMethod(),
              oldPosition.getLine(),
              oldPosition.getCallerPosition(),
              debugInfoProvider.getPcEncoding(currentPc),
              // Outline info is placed exactly on the positions that relate to it so we should
              // only emit it for the first entry.
              firstEntry && oldPosition.isOutline(),
              firstEntry ? oldPosition.getOutlineCallee() : null,
              firstEntry ? oldPosition.getOutlinePositions() : null));
    }
  }

  private enum MappedPositionRange {
    // Single line represent a mapping on the form X:X:<method>:Y:Y.
    SINGLE_LINE,
    // Range to single line allows for a range on the left hand side, X:X':<method>:Y:Y
    RANGE_TO_SINGLE,
    // Same delta is when we have a range on both sides and the delta (line mapping between them)
    // is the same: X:X':<method>:Y:Y' and delta(X,X') = delta(Y,Y')
    SAME_DELTA,
    // Out of range encodes a mapping range that cannot be encoded.
    OUT_OF_RANGE;

    private boolean isSingleLine() {
      return this == SINGLE_LINE;
    }

    private boolean isRangeToSingle() {
      return this == RANGE_TO_SINGLE;
    }

    private boolean isOutOfRange() {
      return this == OUT_OF_RANGE;
    }

    public MappedPositionRange canAddNextMappingToRange(
        MappedPosition lastPosition, MappedPosition currentPosition) {
      if (isOutOfRange()) {
        return this;
      }
      // We allow for ranges being mapped to the same line but not to other ranges:
      //   1:10:void foo():42:42 -> a
      // is OK since retrace(a(:7)) = 42, however, the following is not OK:
      //   1:10:void foo():42:43 -> a
      // since retrace(a(:7)) = 49, which is not correct.
      boolean hasSameRightHandSide = lastPosition.originalLine == currentPosition.originalLine;
      if (hasSameRightHandSide) {
        boolean hasSameLeftHandSide = lastPosition.obfuscatedLine == currentPosition.obfuscatedLine;
        return hasSameLeftHandSide ? SINGLE_LINE : RANGE_TO_SINGLE;
      }
      if (isRangeToSingle()) {
        // We cannot recover a delta encoding if we have had range to single encoding.
        return OUT_OF_RANGE;
      }
      boolean sameDelta =
          currentPosition.originalLine - lastPosition.originalLine
              == currentPosition.obfuscatedLine - lastPosition.obfuscatedLine;
      return sameDelta ? SAME_DELTA : OUT_OF_RANGE;
    }
  }

  private static class OutlineFixupBuilder {

    private static final int MINIFIED_POSITION_REMOVED = -1;

    private final MethodReference outlineMethod;
    private List<MappedPosition> mappedOutlinePositions = null;
    private final List<Pair<MappedRange, Int2IntMap>> mappedOutlineCalleePositions =
        new ArrayList<>();

    private OutlineFixupBuilder(MethodReference outlineMethod) {
      this.outlineMethod = outlineMethod;
    }

    public void setMappedPositionsOutline(List<MappedPosition> mappedPositionsOutline) {
      this.mappedOutlinePositions = mappedPositionsOutline;
    }

    public void addMappedRangeForOutlineCallee(
        MappedRange mappedRangeForOutline, Int2IntMap calleePositions) {
      mappedOutlineCalleePositions.add(Pair.create(mappedRangeForOutline, calleePositions));
    }

    public void fixup() {
      if (mappedOutlinePositions == null || mappedOutlineCalleePositions.isEmpty()) {
        assert mappedOutlinePositions != null : "Mapped outline positions is null";
        assert false : "Mapped outline positions is empty";
        return;
      }
      for (Pair<MappedRange, Int2IntMap> mappingInfo : mappedOutlineCalleePositions) {
        MappedRange mappedRange = mappingInfo.getFirst();
        Int2IntMap positions = mappingInfo.getSecond();
        Int2IntSortedMap map = new Int2IntLinkedOpenHashMap();
        positions.forEach(
            (outlinePosition, calleePosition) -> {
              int minifiedLinePosition =
                  getMinifiedLinePosition(outlinePosition, mappedOutlinePositions);
              if (minifiedLinePosition != MINIFIED_POSITION_REMOVED) {
                map.put(minifiedLinePosition, (int) calleePosition);
              }
            });
        mappedRange.addMappingInformation(
            OutlineCallsiteMappingInformation.create(map, outlineMethod), Unreachable::raise);
      }
    }

    private int getMinifiedLinePosition(
        int originalPosition, List<MappedPosition> mappedPositions) {
      for (MappedPosition mappedPosition : mappedPositions) {
        if (mappedPosition.originalLine == originalPosition) {
          return mappedPosition.obfuscatedLine;
        }
      }
      return MINIFIED_POSITION_REMOVED;
    }
  }
}
