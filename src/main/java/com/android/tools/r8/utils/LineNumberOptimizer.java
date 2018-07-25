// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfPosition;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexDebugEvent;
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
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.naming.ClassNaming;
import com.android.tools.r8.naming.ClassNaming.Builder;
import com.android.tools.r8.naming.MemberNaming;
import com.android.tools.r8.naming.MemberNaming.FieldSignature;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.naming.Range;
import com.google.common.base.Suppliers;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

public class LineNumberOptimizer {

  // EventFilter is a visitor for DebugEvents, splits events into two sinks:
  // - Forwards non-positional events unchanged into a BypassedEventReceiver
  // - Forwards positional events, accumulated into DexDebugPositionStates, into
  //   positionEventReceiver.
  private static class EventFilter implements DexDebugEventVisitor {
    private final BypassedEventReceiver bypassedEventReceiver;
    private final PositionEventReceiver positionEventReceiver;

    private interface BypassedEventReceiver {
      void receiveBypassedEvent(DexDebugEvent event);
    }

    private interface PositionEventReceiver {
      void receivePositionEvent(DexDebugPositionState positionState);
    }

    private final DexDebugPositionState positionState;

    private EventFilter(
        int startLine,
        DexMethod method,
        BypassedEventReceiver bypassedEventReceiver,
        PositionEventReceiver positionEventReceiver) {
      positionState = new DexDebugPositionState(startLine, method);
      this.bypassedEventReceiver = bypassedEventReceiver;
      this.positionEventReceiver = positionEventReceiver;
    }

    @Override
    public void visit(DexDebugEvent.SetPrologueEnd event) {
      bypassedEventReceiver.receiveBypassedEvent(event);
    }

    @Override
    public void visit(DexDebugEvent.SetEpilogueBegin event) {
      bypassedEventReceiver.receiveBypassedEvent(event);
    }

    @Override
    public void visit(DexDebugEvent.StartLocal event) {
      bypassedEventReceiver.receiveBypassedEvent(event);
    }

    @Override
    public void visit(DexDebugEvent.EndLocal event) {
      bypassedEventReceiver.receiveBypassedEvent(event);
    }

    @Override
    public void visit(DexDebugEvent.RestartLocal event) {
      bypassedEventReceiver.receiveBypassedEvent(event);
    }

    @Override
    public void visit(DexDebugEvent.AdvancePC advancePC) {
      positionState.visit(advancePC);
    }

    @Override
    public void visit(DexDebugEvent.AdvanceLine advanceLine) {
      positionState.visit(advanceLine);
    }

    @Override
    public void visit(DexDebugEvent.SetInlineFrame setInlineFrame) {
      positionState.visit(setInlineFrame);
    }

    @Override
    public void visit(DexDebugEvent.Default defaultEvent) {
      positionState.visit(defaultEvent);
      positionEventReceiver.receivePositionEvent(positionState);
    }

    @Override
    public void visit(DexDebugEvent.SetFile setFile) {
      positionState.visit(setFile);
    }
  }

  // PositionRemapper is a stateful function which takes a position (represented by a
  // DexDebugPositionState) and returns a remapped Position.
  private interface PositionRemapper {
    Position createRemappedPosition(
        int line, DexString file, DexMethod method, Position callerPosition);
  }

  private static class IdentityPositionRemapper implements PositionRemapper {
    @Override
    public Position createRemappedPosition(
        int line, DexString file, DexMethod method, Position callerPosition) {
      return new Position(line, file, method, callerPosition);
    }
  }

  private static class OptimizingPositionRemapper implements PositionRemapper {
    private int nextLineNumber = 1;

    @Override
    public Position createRemappedPosition(
        int line, DexString file, DexMethod method, Position callerPosition) {
      Position newPosition = new Position(nextLineNumber, file, method, null);
      ++nextLineNumber;
      return newPosition;
    }
  }

  // PositionEventEmitter is a stateful function which converts a Position into series of
  // position-related DexDebugEvents and puts them into a processedEvents list.
  private static class PositionEventEmitter {
    private final DexItemFactory dexItemFactory;
    private int startLine = -1;
    private final DexMethod method;
    private int previousPc = DexDebugEventBuilder.NO_PC_INFO;
    private Position previousPosition = null;
    private final List<DexDebugEvent> processedEvents;

    private PositionEventEmitter(
        DexItemFactory dexItemFactory, DexMethod method, List<DexDebugEvent> processedEvents) {
      this.dexItemFactory = dexItemFactory;
      this.method = method;
      this.processedEvents = processedEvents;
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
          dexItemFactory);
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
      DexApplication application,
      GraphLense graphLense,
      NamingLens namingLens,
      boolean identityMapping) {
    ClassNameMapper.Builder classNameMapperBuilder = ClassNameMapper.builder();
    // Collect which files contain which classes that need to have their line numbers optimized.
    for (DexProgramClass clazz : application.classes()) {

      // TODO(tamaskenez) fix b/69356670 and remove the conditional skipping.
      if (!clazz.getSynthesizedFrom().isEmpty()) {
        continue;
      }

      IdentityHashMap<DexString, List<DexEncodedMethod>> methodsByRenamedName =
          groupMethodsByRenamedName(namingLens, clazz);

      // At this point we don't know if we really need to add this class to the builder.
      // It depends on whether any methods/fields are renamed or some methods contain positions.
      // Create a supplier which creates a new, cached ClassNaming.Builder on-demand.
      DexString renamedClassName = namingLens.lookupDescriptor(clazz.getType());
      Supplier<ClassNaming.Builder> onDemandClassNamingBuilder =
          Suppliers.memoize(
              () ->
                  classNameMapperBuilder.classNamingBuilder(
                      DescriptorUtils.descriptorToJavaType(renamedClassName.toString()),
                      clazz.toString()));

      // If the class is renamed add it to the classNamingBuilder.
      addClassToClassNaming(clazz, renamedClassName, onDemandClassNamingBuilder);

      // First transfer renamed fields to classNamingBuilder.
      addFieldsToClassNaming(graphLense, namingLens, clazz, onDemandClassNamingBuilder);

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
          sortMethods(methods);
        }

        PositionRemapper positionRemapper =
            identityMapping ? new IdentityPositionRemapper() : new OptimizingPositionRemapper();

        for (DexEncodedMethod method : methods) {
          List<MappedPosition> mappedPositions = new ArrayList<>();

          Code code = method.getCode();
          if (code != null) {
            if (code.isDexCode() && doesContainPositions(code.asDexCode())) {
              optimizeDexCodePositions(
                  method, application, positionRemapper, mappedPositions, identityMapping);
            } else if (code.isCfCode() && doesContainPositions(code.asCfCode())) {
              optimizeCfCodePositions(method, positionRemapper, mappedPositions);
            }
          }

          DexMethod originalMethod = graphLense.getOriginalMethodSignature(method.method);
          MethodSignature originalSignature =
              MethodSignature.fromDexMethod(originalMethod, originalMethod.holder != clazz.type);

          DexString obfuscatedNameDexString = namingLens.lookupName(method.method);
          String obfuscatedName = obfuscatedNameDexString.toString();

          // Add simple "a() -> b" mapping if we won't have any other with concrete line numbers
          if (mappedPositions.isEmpty()) {
            // But only if it's been renamed.
            if (obfuscatedNameDexString != originalMethod.name
                || originalMethod.holder != clazz.type) {
              onDemandClassNamingBuilder
                  .get()
                  .addMappedRange(null, originalSignature, null, obfuscatedName);
            }
            continue;
          }

          Map<DexMethod, MethodSignature> signatures = new IdentityHashMap<>();
          signatures.put(originalMethod, originalSignature);
          Function<DexMethod, MethodSignature> getOriginalMethodSignature =
              m -> {
                DexMethod original = graphLense.getOriginalMethodSignature(m);
                return signatures.computeIfAbsent(
                    original,
                    key ->
                        MethodSignature.fromDexMethod(
                            original, original.holder != clazz.getType()));
              };

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
              // Note that mp.caller and lastPosition.class must be deep-compared since multiple
              // inlining passes lose the canonical property of the positions.
              if ((mp.method != lastPosition.method)
                  || (mp.originalLine - lastPosition.originalLine
                      != mp.obfuscatedLine - lastPosition.obfuscatedLine)
                  || !Objects.equals(mp.caller, lastPosition.caller)) {
                break;
              }
              lastPosition = mp;
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
  private static void addClassToClassNaming(DexProgramClass clazz, DexString renamedClassName,
      Supplier<Builder> onDemandClassNamingBuilder) {
    // We do know we need to create a ClassNaming.Builder if the class itself had been renamed.
    if (!clazz.toString().equals(renamedClassName.toString())) {
      // Not using return value, it's registered in classNameMapperBuilder
      onDemandClassNamingBuilder.get();
    }
  }

  private static void addFieldsToClassNaming(
      GraphLense graphLense,
      NamingLens namingLens,
      DexProgramClass clazz,
      Supplier<Builder> onDemandClassNamingBuilder) {
    clazz.forEachField(
        dexEncodedField -> {
          DexField dexField = dexEncodedField.field;
          DexField originalField = graphLense.getOriginalFieldSignature(dexField);
          DexString renamedName = namingLens.lookupName(dexField);
          if (renamedName != originalField.name || originalField.clazz != clazz.type) {
            FieldSignature originalSignature =
                FieldSignature.fromDexField(originalField, originalField.clazz != clazz.type);
            MemberNaming memberNaming = new MemberNaming(originalSignature, renamedName.toString());
            onDemandClassNamingBuilder.get().addMemberEntry(memberNaming);
          }
        });
  }

  private static IdentityHashMap<DexString, List<DexEncodedMethod>> groupMethodsByRenamedName(
      NamingLens namingLens, DexProgramClass clazz) {
    IdentityHashMap<DexString, List<DexEncodedMethod>> methodsByRenamedName =
        new IdentityHashMap<>(clazz.directMethods().length + clazz.virtualMethods().length);
    for (DexEncodedMethod method : clazz.methods()) {
      // Add method only if renamed or contains positions.
      DexString renamedName = namingLens.lookupName(method.method);
      if (renamedName != method.method.name || doesContainPositions(method)) {
        methodsByRenamedName.computeIfAbsent(renamedName, key -> new ArrayList<>()).add(method);
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
      DexApplication application,
      PositionRemapper positionRemapper,
      List<MappedPosition> mappedPositions,
      boolean identityMapping) {
    // Do the actual processing for each method.
    DexCode dexCode = method.getCode().asDexCode();
    DexDebugInfo debugInfo = dexCode.getDebugInfo();
    List<DexDebugEvent> processedEvents = new ArrayList<>();

    // Our pipeline will be:
    // [debugInfo.events] -> eventFilter -> positionRemapper -> positionEventEmitter ->
    // [processedEvents]
    PositionEventEmitter positionEventEmitter =
        new PositionEventEmitter(application.dexItemFactory, method.method, processedEvents);

    EventFilter eventFilter =
        new EventFilter(
            debugInfo.startLine,
            method.method,
            processedEvents::add,
            positionState -> {
              int currentLine = positionState.getCurrentLine();
              assert currentLine >= 0;
              Position position =
                  positionRemapper.createRemappedPosition(
                      positionState.getCurrentLine(),
                      positionState.getCurrentFile(),
                      positionState.getCurrentMethod(),
                      positionState.getCurrentCallerPosition());
              mappedPositions.add(
                  new MappedPosition(
                      positionState.getCurrentMethod(),
                      currentLine,
                      positionState.getCurrentCallerPosition(),
                      position.line));
              positionEventEmitter.emitPositionEvents(positionState.getCurrentPc(), position);
            });
    for (DexDebugEvent event : debugInfo.events) {
      event.accept(eventFilter);
    }

    DexDebugInfo optimizedDebugInfo =
        new DexDebugInfo(
            positionEventEmitter.getStartLine(),
            debugInfo.parameters,
            processedEvents.toArray(new DexDebugEvent[processedEvents.size()]));

    // TODO(b/111253214) Remove this as soon as we have external tests testing not only the
    // remapping but whether the non-positional debug events remain intact.
    if (identityMapping) {
      assert optimizedDebugInfo.startLine == debugInfo.startLine;
      assert optimizedDebugInfo.events.length == debugInfo.events.length;
      for (int i = 0; i < debugInfo.events.length; ++i) {
        assert optimizedDebugInfo.events[i].equals(debugInfo.events[i]);
      }
    }
    dexCode.setDebugInfo(optimizedDebugInfo);
  }

  private static void optimizeCfCodePositions(
      DexEncodedMethod method,
      PositionRemapper positionRemapper,
      List<MappedPosition> mappedPositions) {
    // Do the actual processing for each method.
    CfCode oldCode = method.getCode().asCfCode();
    List<CfInstruction> oldInstructions = oldCode.getInstructions();
    List<CfInstruction> newInstructions = new ArrayList<>(oldInstructions.size());
    for (int i = 0; i < oldInstructions.size(); ++i) {
      CfInstruction oldInstruction = oldInstructions.get(i);
      CfInstruction newInstruction;
      if (oldInstruction instanceof CfPosition) {
        CfPosition cfPosition = (CfPosition) oldInstruction;
        Position oldPosition = cfPosition.getPosition();
        Position newPosition =
            positionRemapper.createRemappedPosition(
                oldPosition.line, oldPosition.file, oldPosition.method, oldPosition.callerPosition);
        mappedPositions.add(
            new MappedPosition(
                oldPosition.method,
                oldPosition.line,
                oldPosition.callerPosition,
                newPosition.line));
        newInstruction = new CfPosition(cfPosition.getLabel(), newPosition);
      } else {
        newInstruction = oldInstruction;
      }
      newInstructions.add(newInstruction);
    }
    method.setCode(
        new CfCode(
            oldCode.getMethod(),
            oldCode.getMaxStack(),
            oldCode.getMaxLocals(),
            newInstructions,
            oldCode.getTryCatchRanges(),
            oldCode.getLocalVariables()));
  }
}
