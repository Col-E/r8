// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.ObjectToOffsetMapping;
import com.android.tools.r8.graph.OffsetToObjectMapping;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import java.nio.ShortBuffer;

public class DexCheckCast extends DexFormat21c<DexType> {

  public static final int OPCODE = 0x1f;
  public static final String NAME = "CheckCast";
  public static final String SMALI_NAME = "check-cast";

  private final boolean ignoreCompatRules;

  DexCheckCast(int high, BytecodeStream stream, OffsetToObjectMapping mapping) {
    super(high, stream, mapping.getTypeMap());
    this.ignoreCompatRules = false;
  }

  public DexCheckCast(int valueRegister, DexType type, boolean ignoreCompatRules) {
    super(valueRegister, type);
    this.ignoreCompatRules = ignoreCompatRules;
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
  void internalSubSpecify(StructuralSpecification<DexFormat21c<DexType>, ?> spec) {
    spec.withItem(i -> i.BBBB);
  }

  @Override
  public boolean ignoreCompatRules() {
    return ignoreCompatRules;
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

  @Override
  public void write(
      ShortBuffer dest,
      ProgramMethod context,
      GraphLens graphLens,
      GraphLens codeLens,
      ObjectToOffsetMapping mapping,
      LensCodeRewriterUtils rewriter) {
    DexType rewritten = graphLens.lookupType(getType());
    writeFirst(AA, dest);
    write16BitReference(rewritten, dest, mapping);
  }

  @Override
  public DexCheckCast asCheckCast() {
    return this;
  }

  @Override
  public boolean isCheckCast() {
    return true;
  }

  @Override
  public void registerUse(UseRegistry<?> registry) {
    registry.registerCheckCast(getType(), ignoreCompatRules());
  }

  public DexType getType() {
    return BBBB;
  }

  @Override
  public void buildIR(IRBuilder builder) {
    builder.addCheckCast(AA, getType());
  }

  @Override
  public boolean canThrow() {
    return true;
  }
}
