// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.utils.StringUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

import java.util.SortedSet;
import java.util.TreeSet;

public class DexDebugEntry {

  public final boolean lineEntry;
  public final int address;
  public final DexString sourceFile;
  public final boolean prologueEnd;
  public final boolean epilogueBegin;
  public final Int2ObjectMap<DebugLocalInfo> locals;
  private final Position position;

  public DexDebugEntry(
      boolean lineEntry,
      int address,
      DexString sourceFile,
      boolean prologueEnd,
      boolean epilogueBegin,
      Int2ObjectMap<DebugLocalInfo> locals,
      Position position) {
    this.lineEntry = lineEntry;
    this.address = address;
    this.sourceFile = sourceFile;
    this.prologueEnd = prologueEnd;
    this.epilogueBegin = epilogueBegin;
    this.locals = locals;
    this.position = position;
    assert position != null;
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
    builder.append(", ").append(position);
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

  public Position getPosition() {
    return position;
  }

  public int getLine() {
    return position.getLine();
  }
}
