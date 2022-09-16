// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.positions;

import static com.android.tools.r8.utils.positions.PositionUtils.remapAndAdd;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfPosition;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.ProgramMethod;
import java.util.ArrayList;
import java.util.List;

public class ClassFilePositionToMappedRangeMapper implements PositionToMappedRangeMapper {

  private final AppView<?> appView;

  public ClassFilePositionToMappedRangeMapper(AppView<?> appView) {
    this.appView = appView;
  }

  @Override
  public List<MappedPosition> getMappedPositions(
      ProgramMethod method,
      PositionRemapper positionRemapper,
      boolean hasOverloads,
      boolean canUseDexPc,
      int pcEncodingCutoff) {
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

  @Override
  public void updateDebugInfoInCodeObjects() {
    // Intentionally empty.
  }
}
