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

public class ConstClass extends Format21c<DexType> {

  public static final int OPCODE = 0x1c;
  public static final String NAME = "ConstClass";
  public static final String SMALI_NAME = "const-class";

  ConstClass(int high, BytecodeStream stream, OffsetToObjectMapping mapping) {
    super(high, stream, mapping.getTypeMap());
  }

  public ConstClass(int dest, DexType type) {
    super(dest, type);
  }

  @Override
  int internalCompareBBBB(Format21c<?> other) {
    return BBBB.slowCompareTo((DexType) other.BBBB);
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

  @Override
  public void write(
      ShortBuffer dest,
      ProgramMethod context,
      GraphLens graphLens,
      ObjectToOffsetMapping mapping,
      LensCodeRewriterUtils rewriter) {
    DexType rewritten = graphLens.lookupType(getType());
    writeFirst(AA, dest);
    write16BitReference(rewritten, dest, mapping);
  }

  @Override
  public void registerUse(UseRegistry registry) {
    registry.registerConstClass(getType(), null);
  }

  public DexType getType() {
    return BBBB;
  }

  @Override
  public void buildIR(IRBuilder builder) {
    builder.addConstClass(AA, getType());
  }

  @Override
  public boolean canThrow() {
    return true;
  }

  @Override
  public ConstClass asConstClass() {
    return this;
  }

  @Override
  public boolean isConstClass() {
    return true;
  }
}
