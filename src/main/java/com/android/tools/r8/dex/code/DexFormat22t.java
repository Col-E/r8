// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.ObjectToOffsetMapping;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.If.Type;
import com.android.tools.r8.ir.code.ValueTypeConstraint;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.utils.RetracerForCodePrinting;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import java.nio.ShortBuffer;

public abstract class DexFormat22t extends DexBase2Format {

  public final byte A;
  public final byte B;
  public /* offset */ short CCCC;

  private static void specify(StructuralSpecification<DexFormat22t, ?> spec) {
    spec.withInt(i -> i.A).withInt(i -> i.B).withInt(i -> i.CCCC);
  }

  // vB | vA | op | +CCCC
  DexFormat22t(int high, BytecodeStream stream) {
    super(stream);
    A = (byte) (high & 0xf);
    B = (byte) ((high >> 4) & 0xf);
    CCCC = readSigned16BitValue(stream);
  }

  DexFormat22t(int register1, int register2, int offset) {
    assert 0 <= register1 && register1 <= Constants.U4BIT_MAX;
    assert 0 <= register2 && register2 <= Constants.U4BIT_MAX;
    assert Short.MIN_VALUE <= offset && offset <= Short.MAX_VALUE;
    A = (byte) register1;
    B = (byte) register2;
    CCCC = (short) offset;
  }

  @Override
  public void write(
      ShortBuffer dest,
      ProgramMethod context,
      GraphLens graphLens,
      ObjectToOffsetMapping mapping,
      LensCodeRewriterUtils rewriter) {
    writeFirst(B, A, dest);
    write16BitValue(CCCC, dest);
  }

  @Override
  public final int hashCode() {
    return ((CCCC << 8) | (B << 4) | A) ^ getClass().hashCode();
  }

  @Override
  final int internalAcceptCompareTo(DexInstruction other, CompareToVisitor visitor) {
    return visitor.visit(this, (DexFormat22t) other, DexFormat22t::specify);
  }

  @Override
  final void internalAcceptHashing(HashingVisitor visitor) {
    visitor.visit(this, DexFormat22t::specify);
  }

  public abstract Type getType();

  public abstract ValueTypeConstraint getOperandTypeConstraint();

  @Override
  public int[] getTargets() {
    return new int[] {CCCC, getSize()};
  }

  @Override
  public void buildIR(IRBuilder builder) {
    int offset = getOffset();
    int size = getSize();
    builder.addIf(getType(), getOperandTypeConstraint(), A, B, offset + CCCC, offset + size);
  }

  @Override
  public String toString(RetracerForCodePrinting retracer) {
    return formatString("v" + A + ", v" + B + ", " + formatRelativeOffset(CCCC));
  }

  @Override
  public String toSmaliString(RetracerForCodePrinting retracer) {
    return formatSmaliString("v" + A + ", v" + B + ", :label_" + (getOffset() + CCCC));
  }

  @Override
  public void collectIndexedItems(
      AppView<?> appView,
      IndexedItemCollection indexedItems,
      ProgramMethod context,
      LensCodeRewriterUtils rewriter) {
    // No references.
  }
}
