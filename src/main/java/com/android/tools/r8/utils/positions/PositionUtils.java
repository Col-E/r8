// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.positions;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfPosition;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexDebugEvent;
import com.android.tools.r8.graph.DexDebugInfo;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Pair;
import java.util.List;

public class PositionUtils {

  public static Position remapAndAdd(
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

  public static boolean mustHaveResidualDebugInfo(
      InternalOptions options, DexEncodedMethod method) {
    Code code = method.getCode();
    if (code == null) {
      return false;
    }
    if (code.isDexCode()) {
      return mustHaveResidualDebugInfo(options, code.asDexCode());
    } else if (code.isCfCode()) {
      return mustHaveResidualDebugInfo(code.asCfCode());
    }
    return false;
  }

  private static boolean mustHaveResidualDebugInfo(InternalOptions options, DexCode code) {
    // All code objects must have debug info if discarding it is not allowed.
    if (!options.allowDiscardingResidualDebugInfo()) {
      return true;
    }
    // Otherwise debug info is only needed for code sequences with at least one position.
    DexDebugInfo debugInfo = code.getDebugInfo();
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

  private static boolean mustHaveResidualDebugInfo(CfCode code) {
    List<CfInstruction> instructions = code.getInstructions();
    for (CfInstruction instruction : instructions) {
      if (instruction instanceof CfPosition) {
        return true;
      }
    }
    return false;
  }
}
