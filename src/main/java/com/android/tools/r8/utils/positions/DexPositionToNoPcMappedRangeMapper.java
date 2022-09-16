// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.positions;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
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
import com.android.tools.r8.graph.DexDebugInfo;
import com.android.tools.r8.graph.DexDebugInfo.EventBasedDebugInfo;
import com.android.tools.r8.graph.DexDebugInfoForSingleLineMethod;
import com.android.tools.r8.graph.DexDebugPositionState;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Position.OutlineCallerPosition;
import com.android.tools.r8.ir.code.Position.OutlineCallerPosition.OutlineCallerPositionBuilder;
import com.android.tools.r8.ir.code.Position.OutlinePosition;
import com.android.tools.r8.ir.code.Position.PositionBuilder;
import com.android.tools.r8.ir.code.Position.SourcePosition;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.InternalOptions.LineNumberOptimization;
import java.util.ArrayList;
import java.util.List;

public class DexPositionToNoPcMappedRangeMapper {

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

  private final AppView<?> appView;
  private final boolean isIdentityMapping;

  public DexPositionToNoPcMappedRangeMapper(AppView<?> appView) {
    this.appView = appView;
    isIdentityMapping = appView.options().lineNumberOptimization == LineNumberOptimization.OFF;
  }

  public List<MappedPosition> optimizeDexCodePositions(
      ProgramMethod method, PositionRemapper positionRemapper, boolean hasOverloads) {
    List<MappedPosition> mappedPositions = new ArrayList<>();
    // Do the actual processing for each method.
    DexApplication application = appView.appInfo().app();
    DexCode dexCode = method.getDefinition().getCode().asDexCode();
    EventBasedDebugInfo debugInfo =
        getEventBasedDebugInfo(method.getDefinition(), dexCode, appView);

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
            Position currentPosition =
                PositionUtils.remapAndAdd(position, positionRemapper, mappedPositions);
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

    assert !isIdentityMapping
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
      return DexDebugInfo.createEventBasedInfoForMethodWithoutDebugInfo(
          method, appView.dexItemFactory());
    }
    assert method.getParameters().size() == dexCode.getDebugInfo().getParameterCount();
    EventBasedDebugInfo debugInfo =
        DexDebugInfo.convertToEventBased(dexCode, appView.dexItemFactory());
    assert debugInfo != null;
    return debugInfo;
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

  private static boolean verifyIdentityMapping(
      EventBasedDebugInfo originalDebugInfo, EventBasedDebugInfo optimizedDebugInfo) {
    assert optimizedDebugInfo.startLine == originalDebugInfo.startLine;
    assert optimizedDebugInfo.events.length == originalDebugInfo.events.length;
    for (int i = 0; i < originalDebugInfo.events.length; ++i) {
      assert optimizedDebugInfo.events[i].equals(originalDebugInfo.events[i]);
    }
    return true;
  }
}
