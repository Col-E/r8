// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.positions;

import static com.android.tools.r8.utils.positions.PositionUtils.remapAndAdd;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfPosition;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Position.SyntheticPosition;
import com.android.tools.r8.utils.Pair;
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
    return appView.options().getTestingOptions().usePcEncodingInCfForTesting
        ? getPcEncodedPositions(method, positionRemapper)
        : getMappedPositionsRemapped(method, positionRemapper, hasOverloads);
  }

  @Override
  public void updateDebugInfoInCodeObjects() {
    // Intentionally empty.
  }

  private List<MappedPosition> getMappedPositionsRemapped(
      ProgramMethod method, PositionRemapper positionRemapper, boolean hasOverloads) {
    List<MappedPosition> mappedPositions = new ArrayList<>();
    // Do the actual processing for each method.
    CfCode oldCode = method.getDefinition().getCode().asCfCode();
    List<CfInstruction> oldInstructions = oldCode.getInstructions();
    List<CfInstruction> newInstructions = new ArrayList<>(oldInstructions.size());
    boolean seenPosition = false;
    for (CfInstruction oldInstruction : oldInstructions) {
      CfInstruction newInstruction;
      if (oldInstruction.isPosition()) {
        seenPosition = true;
        CfPosition cfPosition = oldInstruction.asPosition();
        newInstruction =
            new CfPosition(
                cfPosition.getLabel(),
                remapAndAdd(cfPosition.getPosition(), positionRemapper, mappedPositions));
      } else {
        newInstruction = oldInstruction;
      }
      newInstructions.add(newInstruction);
    }
    if (!seenPosition && hasOverloads) {
      // If a method with overloads does not have an actual position then map it to the implicit
      // preamble position.
      DexMethod reference = method.getReference();
      DexMethod original = appView.graphLens().getOriginalMethodSignature(reference);
      CfPosition preamblePositionForOverload =
          new CfPosition(
              new CfLabel(),
              remapAndAdd(
                  SyntheticPosition.builder().setMethod(original).setLine(0).build(),
                  positionRemapper,
                  mappedPositions));
      List<CfInstruction> shiftedPositions = new ArrayList<>(oldInstructions.size() + 2);
      shiftedPositions.add(preamblePositionForOverload);
      shiftedPositions.add(preamblePositionForOverload.getLabel());
      shiftedPositions.addAll(newInstructions);
      newInstructions = shiftedPositions;
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

  @SuppressWarnings("UnusedVariable")
  private List<MappedPosition> getPcEncodedPositions(
      ProgramMethod method, PositionRemapper positionRemapper) {
    List<MappedPosition> mappedPositions = new ArrayList<>();
    // Do the actual processing for each method.
    CfCode oldCode = method.getDefinition().getCode().asCfCode();
    List<CfInstruction> oldInstructions = oldCode.getInstructions();
    List<CfInstruction> newInstructions = new ArrayList<>(oldInstructions.size() * 3);
    Position currentPosition = null;
    boolean isFirstEntry = false;
    for (CfInstruction oldInstruction : oldInstructions) {
      if (oldInstruction.isPosition()) {
        CfPosition cfPosition = oldInstruction.asPosition();
        currentPosition = cfPosition.getPosition();
        isFirstEntry = true;
      } else {
        if (currentPosition != null) {
          Pair<Position, Position> remappedPosition =
              positionRemapper.createRemappedPosition(currentPosition);
          Position oldPosition = remappedPosition.getFirst();
          Position newPosition = remappedPosition.getSecond();
          mappedPositions.add(new MappedPosition(oldPosition, newPosition.getLine()));
          CfPosition position = new CfPosition(new CfLabel(), newPosition);
          newInstructions.add(position);
          newInstructions.add(position.getLabel());
        }
        isFirstEntry = false;
        newInstructions.add(oldInstruction);
      }
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
}
