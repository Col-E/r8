// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.lightir;

import com.android.tools.r8.graph.DexItem;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;

public class LIRBuilder {

  private final ByteArrayWriter byteWriter = new ByteArrayWriter();
  private final LIRWriter writer = new LIRWriter(byteWriter);
  private final Reference2IntMap<DexItem> constants;

  public LIRBuilder() {
    constants = new Reference2IntOpenHashMap<>();
  }

  private int getConstantIndex(DexItem item) {
    int nextIndex = constants.size();
    Integer oldIndex = constants.putIfAbsent(item, nextIndex);
    return oldIndex != null ? oldIndex : nextIndex;
  }

  public LIRBuilder addNop() {
    writer.writeOneByteInstruction(LIROpcodes.NOP);
    return this;
  }

  public LIRBuilder addConstNull() {
    writer.writeOneByteInstruction(LIROpcodes.ACONST_NULL);
    return this;
  }

  public LIRBuilder addConstInt(int value) {
    if (0 <= value && value <= 5) {
      writer.writeOneByteInstruction(LIROpcodes.ICONST_0 + value);
    } else {
      writer.writeInstruction(LIROpcodes.ICONST, ByteUtils.intEncodingSize(value));
      ByteUtils.writeEncodedInt(value, writer::writeOperand);
    }
    return this;
  }

  public LIRCode build() {
    int constantsCount = constants.size();
    DexItem[] constantTable = new DexItem[constantsCount];
    constants.forEach((item, index) -> constantTable[index] = item);
    return new LIRCode(constantTable, byteWriter.toByteArray());
  }
}
