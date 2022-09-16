// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.positions;

import com.android.tools.r8.debuginfo.DebugRepresentation;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexDebugEvent;
import com.android.tools.r8.graph.DexDebugEvent.Default;
import com.android.tools.r8.graph.DexDebugEventVisitor;
import com.android.tools.r8.graph.DexDebugInfo;
import com.android.tools.r8.graph.DexDebugInfo.EventBasedDebugInfo;
import com.android.tools.r8.graph.DexDebugInfoForSingleLineMethod;
import com.android.tools.r8.graph.DexDebugPositionState;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Position.OutlineCallerPosition;
import com.android.tools.r8.ir.code.Position.OutlineCallerPosition.OutlineCallerPositionBuilder;
import com.android.tools.r8.ir.code.Position.OutlinePosition;
import com.android.tools.r8.ir.code.Position.PositionBuilder;
import com.android.tools.r8.ir.code.Position.SourcePosition;
import com.android.tools.r8.utils.BooleanBox;
import com.android.tools.r8.utils.IntBox;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.positions.PositionToMappedRangeMapper.PcBasedDebugInfoRecorder;
import java.util.ArrayList;
import java.util.List;

public class DexPositionToPcMappedRangeMapper {

  private final AppView<?> appView;
  private final PcBasedDebugInfoRecorder pcBasedDebugInfo;

  public DexPositionToPcMappedRangeMapper(
      AppView<?> appView, PcBasedDebugInfoRecorder pcBasedDebugInfo) {
    this.appView = appView;
    this.pcBasedDebugInfo = pcBasedDebugInfo;
  }

  public List<MappedPosition> optimizeDexCodePositionsForPc(
      ProgramMethod method, PositionRemapper positionRemapper, int pcEncodingCutoff) {
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
                  pcBasedDebugInfo,
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
          pcBasedDebugInfo,
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
      pcBasedDebugInfo.recordSingleLineFor(method, pcEncodingCutoff);
    } else {
      pcBasedDebugInfo.recordPcMappingFor(method, pcEncodingCutoff);
    }
    return mappedPositions;
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
