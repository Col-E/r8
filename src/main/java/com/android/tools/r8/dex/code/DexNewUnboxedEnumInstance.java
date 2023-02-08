// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.ObjectToOffsetMapping;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import java.nio.ShortBuffer;

/** The dex representation of {@link com.android.tools.r8.ir.code.NewUnboxedEnumInstance}. */
public class DexNewUnboxedEnumInstance extends DexFormat21c<DexType> {

  public static final int OPCODE = 0x22;
  public static final String NAME = "NewUnboxedEnumInstance";
  public static final String SMALI_NAME = "new-unboxed-enum-instance";

  private final int ordinal;

  public DexNewUnboxedEnumInstance(int AA, DexType BBBB, int ordinal) {
    super(AA, BBBB);
    this.ordinal = ordinal;
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
    throw new Unreachable();
  }

  @Override
  void internalSubSpecify(StructuralSpecification<DexFormat21c<DexType>, ?> spec) {
    spec.withItem(i -> i.BBBB);
  }

  @Override
  public void collectIndexedItems(
      AppView<?> appView,
      IndexedItemCollection indexedItems,
      ProgramMethod context,
      LensCodeRewriterUtils rewriter) {
    throw new Unreachable();
  }

  @Override
  public void write(
      ShortBuffer dest,
      ProgramMethod context,
      GraphLens graphLens,
      GraphLens codeLens,
      ObjectToOffsetMapping mapping,
      LensCodeRewriterUtils rewriter) {
    throw new Unreachable();
  }

  @Override
  public void registerUse(UseRegistry<?> registry) {
    registry.registerNewUnboxedEnumInstance(getType());
  }

  public DexType getType() {
    return BBBB;
  }

  @Override
  public void buildIR(IRBuilder builder) {
    builder.addNewUnboxedEnumInstance(AA, getType(), ordinal);
  }

  @Override
  public boolean canThrow() {
    return true;
  }
}
