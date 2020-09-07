// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.code;

import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.ObjectToOffsetMapping;
import com.android.tools.r8.graph.OffsetToObjectMapping;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import java.nio.ShortBuffer;

public class InstanceOf extends Format22c<DexType> {

  public static final int OPCODE = 0x20;
  public static final String NAME = "InstanceOf";
  public static final String SMALI_NAME = "instance-of";

  InstanceOf(int high, BytecodeStream stream, OffsetToObjectMapping mapping) {
    super(high, stream, mapping.getTypeMap());
  }

  public InstanceOf(int dest, int value, DexType type) {
    super(dest, value, type);
  }

  @Override
  public InstanceOf asInstanceOf() {
    return this;
  }

  @Override
  public boolean isInstanceOf() {
    return true;
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public String getSmaliName() {
    return SMALI_NAME;
  }

  @Override
  public int getOpcode() {
    return OPCODE;
  }

  @Override
  public void collectIndexedItems(
      IndexedItemCollection indexedItems,
      ProgramMethod context,
      GraphLens graphLens,
      LensCodeRewriterUtils rewriter) {
    DexType rewritten = graphLens.lookupType(getType());
    rewritten.collectIndexedItems(indexedItems);
  }

  public DexType getType() {
    return CCCC;
  }

  @Override
  public void registerUse(UseRegistry registry) {
    registry.registerInstanceOf(getType());
  }

  @Override
  public void buildIR(IRBuilder builder) {
    builder.addInstanceOf(A, B, getType());
  }

  @Override
  public boolean canThrow() {
    return true;
  }

  @Override
  public void write(
      ShortBuffer dest,
      ProgramMethod context,
      GraphLens graphLens,
      ObjectToOffsetMapping mapping,
      LensCodeRewriterUtils rewriter) {
    DexType lookup = graphLens.lookupType(getType());
    writeFirst(B, A, dest);
    write16BitReference(lookup, dest, mapping);
  }
}
