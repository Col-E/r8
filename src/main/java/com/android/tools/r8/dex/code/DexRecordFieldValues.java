// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.ObjectToOffsetMapping;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.utils.RetracerForCodePrinting;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.nio.ShortBuffer;
import java.util.Arrays;

public class DexRecordFieldValues extends DexInstruction {

  public static final String NAME = "RecordFieldValues";
  public static final String SMALI_NAME = "record-field-values*";

  private final int outRegister;
  private final int[] arguments;
  private final DexField[] fields;

  public DexRecordFieldValues(int outRegister, int[] arguments, DexField[] fields) {
    this.outRegister = outRegister;
    this.arguments = arguments;
    this.fields = fields;
  }

  @Override
  public void collectIndexedItems(
      AppView<?> appView,
      IndexedItemCollection indexedItems,
      ProgramMethod context,
      LensCodeRewriterUtils rewriter) {
    for (DexField field : fields) {
      field.collectIndexedItems(appView, indexedItems);
    }
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
        "DexRecordFieldValues instructions should always be rewritten into NewArray");
  }

  @Override
  public int getSize() {
    return 2;
  }

  @Override
  int getCompareToId() {
    return DexCompareHelper.DEX_RECORD_FIELD_VALUES_COMPARE_ID;
  }

  @Override
  final int internalAcceptCompareTo(DexInstruction other, CompareToVisitor visitor) {
    return visitor.visit(this, (DexRecordFieldValues) other, DexRecordFieldValues::specify);
  }

  @Override
  final void internalAcceptHashing(HashingVisitor visitor) {
    visitor.visit(this, DexRecordFieldValues::specify);
  }

  private static void specify(StructuralSpecification<DexRecordFieldValues, ?> spec) {
    spec.withItemArray(i -> i.fields);
  }

  private void appendArguments(StringBuilder builder) {
    builder.append("{ ");
    for (int i = 0; i < arguments.length; i++) {
      if (i != 0) {
        builder.append(",");
      }
      builder.append("v").append(arguments[i]);
    }
    builder.append(" }");
  }

  @Override
  public String toString(RetracerForCodePrinting retracer) {
    StringBuilder sb = new StringBuilder();
    sb.append("v").append(outRegister).append(" ");
    appendArguments(sb);
    return formatString(sb.toString());
  }

  @Override
  public String toSmaliString(RetracerForCodePrinting retracer) {
    return toString(retracer);
  }

  @Override
  public void write(
      ShortBuffer dest,
      ProgramMethod context,
      GraphLens graphLens,
      ObjectToOffsetMapping mapping,
      LensCodeRewriterUtils rewriter) {
    throw new Unreachable(
        "DexRecordFieldValues instructions should always be rewritten into NewArray");
  }

  @Override
  public boolean isRecordFieldValues() {
    return true;
  }

  @Override
  public void registerUse(UseRegistry registry) {
    registry.registerRecordFieldValues(fields);
  }

  @Override
  public void buildIR(IRBuilder builder) {
    IntList parameters = new IntArrayList();
    for (int i = 0; i < arguments.length; i++) {
      parameters.add(arguments[i]);
    }
    builder.addRecordFieldValues(fields, parameters, outRegister);
  }

  @Override
  public int hashCode() {
    return 31 * getClass().hashCode() + Arrays.hashCode(fields);
  }

  @Override
  public boolean canThrow() {
    return true;
  }
}
