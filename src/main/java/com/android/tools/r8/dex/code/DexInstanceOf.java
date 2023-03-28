// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ObjectToOffsetMapping;
import com.android.tools.r8.graph.OffsetToObjectMapping;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import java.nio.ShortBuffer;

public class DexInstanceOf extends DexFormat22c<DexType> {

  public static final int OPCODE = 0x20;
  public static final String NAME = "InstanceOf";
  public static final String SMALI_NAME = "instance-of";

  DexInstanceOf(int high, BytecodeStream stream, OffsetToObjectMapping mapping) {
    super(high, stream, mapping.getTypeMap());
  }

  public DexInstanceOf(int dest, int value, DexType type) {
    super(dest, value, type);
  }

  @Override
  public DexInstanceOf asInstanceOf() {
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
      AppView<?> appView,
      IndexedItemCollection indexedItems,
      ProgramMethod context,
      LensCodeRewriterUtils rewriter) {
    DexType rewritten = appView.graphLens().lookupType(getType());
    rewritten.collectIndexedItems(appView, indexedItems);
  }

  public DexType getType() {
    return CCCC;
  }

  @Override
  public void registerUse(UseRegistry<?> registry) {
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
      GraphLens codeLens,
      ObjectToOffsetMapping mapping,
      LensCodeRewriterUtils rewriter) {
    DexType lookup = graphLens.lookupType(getType());
    writeFirst(B, A, dest);
    write16BitReference(lookup, dest, mapping);
  }
}
