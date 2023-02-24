// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.positions;

import com.android.tools.r8.debuginfo.DebugRepresentation;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexDebugEvent;
import com.android.tools.r8.graph.DexDebugEvent.Default;
import com.android.tools.r8.graph.DexDebugEventVisitor;
import com.android.tools.r8.graph.DexDebugInfo;
import com.android.tools.r8.graph.DexDebugInfo.EventBasedDebugInfo;
import com.android.tools.r8.graph.DexDebugPositionState;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.Position;
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
            Position currentPosition = getPosition();
            if (lastPosition.getSecond() != null) {
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
          }
        };

    for (DexDebugEvent event : debugInfo.events) {
      event.accept(visitor);
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
    pcBasedDebugInfo.recordPcMappingFor(method, pcEncodingCutoff);
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
      mappedPositions.add(
          new MappedPosition(oldPosition, debugInfoProvider.getPcEncoding(currentPc)));
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

}
