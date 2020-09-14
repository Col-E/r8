// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.code;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.ObjectToOffsetMapping;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.If.Type;
import com.android.tools.r8.ir.code.ValueTypeConstraint;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.utils.ComparatorUtils;
import java.nio.ShortBuffer;

public abstract class Format22t extends Base2Format {

  public final byte A;
  public final byte B;
  public /* offset */ short CCCC;

  // vB | vA | op | +CCCC
  Format22t(int high, BytecodeStream stream) {
    super(stream);
    A = (byte) (high & 0xf);
    B = (byte) ((high >> 4) & 0xf);
    CCCC = readSigned16BitValue(stream);
  }

  Format22t(int register1, int register2, int offset) {
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
  final int internalCompareTo(Instruction other) {
    Format22t o = (Format22t) other;
    return ComparatorUtils.compareInts(A, o.A, B, o.B, CCCC, o.CCCC);
  }

  public abstract Type getType();

  public abstract ValueTypeConstraint getOperandTypeConstraint();

  @Override
  public int[] getTargets() {
    return new int[]{CCCC, getSize()};
  }

  @Override
  public void buildIR(IRBuilder builder) {
    int offset = getOffset();
    int size = getSize();
    builder.addIf(getType(), getOperandTypeConstraint(), A, B, offset + CCCC, offset + size);
  }

  @Override
  public String toString(ClassNameMapper naming) {
    return formatString("v" + A + ", v" + B + ", " + formatRelativeOffset(CCCC));
  }

  @Override
  public String toSmaliString(ClassNameMapper naming) {
    return formatSmaliString("v" + A + ", v" + B + ", :label_" + (getOffset() + CCCC));
  }

  @Override
  public void collectIndexedItems(
      IndexedItemCollection indexedItems,
      ProgramMethod context,
      GraphLens graphLens,
      LensCodeRewriterUtils rewriter) {
    // No references.
  }
}
