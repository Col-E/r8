// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.ObjectToOffsetMapping;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.naming.dexitembasedstring.NameComputationInfo;
import com.android.tools.r8.utils.RetracerForCodePrinting;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import java.nio.ShortBuffer;

public class DexItemBasedConstString extends DexFormat21c<DexReference> {

  public static final String NAME = "DexItemBasedConstString";
  public static final String SMALI_NAME = "const-string*";

  private final NameComputationInfo<?> nameComputationInfo;

  public DexItemBasedConstString(
      int register, DexReference string, NameComputationInfo<?> nameComputationInfo) {
    super(register, string);
    this.nameComputationInfo = nameComputationInfo;
  }

  public DexReference getItem() {
    return BBBB;
  }

  public NameComputationInfo<?> getNameComputationInfo() {
    return nameComputationInfo;
  }

  @Override
  public void collectIndexedItems(
      AppView<?> appView,
      IndexedItemCollection indexedItems,
      ProgramMethod context,
      LensCodeRewriterUtils rewriter) {
    getItem().collectIndexedItems(appView, indexedItems);
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
    throw new Unreachable(
        "DexItemBasedConstString instructions should always be rewritten into ConstString");
  }

  @Override
  int getCompareToId() {
    return DexCompareHelper.DEX_ITEM_CONST_STRING_COMPARE_ID;
  }

  @Override
  void internalSubSpecify(StructuralSpecification<DexFormat21c<DexReference>, ?> spec) {
    spec.withDexReference(i -> i.BBBB);
  }

  @Override
  public DexItemBasedConstString asDexItemBasedConstString() {
    return this;
  }

  @Override
  public boolean isDexItemBasedConstString() {
    return true;
  }

  @Override
  public String toString(RetracerForCodePrinting retracer) {
    // TODO(christofferqa): Apply mapping to item.
    return formatString("v" + AA + ", \"" + BBBB.toString() + "\"");
  }

  @Override
  public String toSmaliString(RetracerForCodePrinting retracer) {
    // TODO(christofferqa): Apply mapping to item.
    return formatSmaliString("v" + AA + ", \"" + BBBB.toString() + "\"");
  }

  @Override
  public void write(
      ShortBuffer dest,
      ProgramMethod context,
      GraphLens graphLens,
      ObjectToOffsetMapping mapping,
      LensCodeRewriterUtils rewriter) {
    throw new Unreachable(
        "DexItemBasedConstString instructions should always be rewritten into ConstString");
  }

  @Override
  public void registerUse(UseRegistry<?> registry) {
    if (nameComputationInfo.needsToRegisterReference()) {
      assert getItem().isDexType();
      registry.registerTypeReference(getItem().asDexType());
    }
  }

  @Override
  public void buildIR(IRBuilder builder) {
    builder.addDexItemBasedConstString(AA, (DexReference) BBBB, nameComputationInfo);
  }

  @Override
  public boolean canThrow() {
    return true;
  }
}
