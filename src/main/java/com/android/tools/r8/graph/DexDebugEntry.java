// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Position.OutlineCallerPosition;
import com.android.tools.r8.ir.code.Position.OutlineCallerPosition.OutlineCallerPositionBuilder;
import com.android.tools.r8.ir.code.Position.OutlinePosition;
import com.android.tools.r8.ir.code.Position.PositionBuilder;
import com.android.tools.r8.ir.code.Position.SourcePosition;
import com.android.tools.r8.utils.Int2StructuralItemArrayMap;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;

public class DexDebugEntry {

  public final boolean lineEntry;
  public final int address;
  public final int line;
  public final DexString sourceFile;
  public final boolean prologueEnd;
  public final boolean epilogueBegin;
  public final Map<Integer, DebugLocalInfo> locals;
  public final DexMethod method;
  public final Position callerPosition;
  public final boolean isOutline;
  public final DexMethod outlineCallee;
  public final Int2StructuralItemArrayMap<Position> outlineCallerPositions;

  public DexDebugEntry(
      boolean lineEntry,
      int address,
      int line,
      DexString sourceFile,
      boolean prologueEnd,
      boolean epilogueBegin,
      ImmutableMap<Integer, DebugLocalInfo> locals,
      DexMethod method,
      Position callerPosition,
      boolean isOutline,
      DexMethod outlineCallee,
      Int2StructuralItemArrayMap<Position> outlineCallerPositions) {
    this.lineEntry = lineEntry;
    this.address = address;
    this.line = line;
    this.sourceFile = sourceFile;
    this.prologueEnd = prologueEnd;
    this.epilogueBegin = epilogueBegin;
    this.locals = locals;
    this.method = method;
    assert method != null;
    this.callerPosition = callerPosition;
    this.isOutline = isOutline;
    this.outlineCallee = outlineCallee;
    this.outlineCallerPositions = outlineCallerPositions;
  }

  @Override
  public String toString() {
    return toString(true);
  }

  public String toString(boolean withPcPrefix) {
    StringBuilder builder = new StringBuilder();
    if (withPcPrefix) {
      builder.append("pc ");
    }
    builder.append(StringUtils.hexString(address, 2));
    if (sourceFile != null) {
      builder.append(", file ").append(sourceFile);
    }
    builder.append(", line ").append(line);
    if (callerPosition != null) {
      builder.append(":").append(method.name);
      Position caller = callerPosition;
      while (caller != null) {
        builder.append(";").append(caller.getLine()).append(":").append(caller.getMethod().name);
        caller = caller.getCallerPosition();
      }
    }
    if (isOutline) {
      builder.append(", isOutline = true");
    }
    if (outlineCallee != null) {
      builder.append(", outlineCallee = ").append(outlineCallee);
    }
    if (outlineCallerPositions != null) {
      builder.append(", outlineCallerPositions = ").append(outlineCallerPositions);
    }
    if (prologueEnd) {
      builder.append(", prologue_end = true");
    }
    if (epilogueBegin) {
      builder.append(", epilogue_begin = true");
    }
    if (!locals.isEmpty()) {
      builder.append(", locals: [");
      SortedSet<Integer> keys = new TreeSet<>(locals.keySet());
      boolean first = true;
      for (Integer register : keys) {
        if (first) {
          first = false;
        } else {
          builder.append(", ");
        }
        builder.append(register).append(" -> ").append(locals.get(register));
      }
      builder.append("]");
    }
    return builder.toString();
  }

  public Position toPosition(Function<Position, Position> canonicalizeCallerPosition) {
    PositionBuilder<?, ?> positionBuilder;
    if (outlineCallee != null) {
      OutlineCallerPositionBuilder outlineCallerPositionBuilder =
          OutlineCallerPosition.builder().setOutlineCallee(outlineCallee).setIsOutline(isOutline);
      outlineCallerPositions.forEach(outlineCallerPositionBuilder::addOutlinePosition);
      positionBuilder = outlineCallerPositionBuilder;
    } else if (isOutline) {
      positionBuilder = OutlinePosition.builder();
    } else {
      positionBuilder = SourcePosition.builder().setFile(sourceFile);
    }
    return positionBuilder
        .setLine(line)
        .setMethod(method)
        .setCallerPosition(canonicalizeCallerPosition.apply(callerPosition))
        .build();
  }
}
