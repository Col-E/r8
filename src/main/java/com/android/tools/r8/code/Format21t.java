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

public abstract class Format21t extends Base2Format {

  public final short AA;
  public /* offset */ short BBBB;

  // AA | op | +BBBB
  Format21t(int high, BytecodeStream stream) {
    super(stream);
    AA = (short) high;
    BBBB = readSigned16BitValue(stream);
  }

  Format21t(int register, int offset) {
    assert Short.MIN_VALUE <= offset && offset <= Short.MAX_VALUE;
    assert 0 <= register && register <= Constants.U8BIT_MAX;
    AA = (short) register;
    BBBB = (short) offset;
  }

  @Override
  public void write(
      ShortBuffer dest,
      ProgramMethod context,
      GraphLens graphLens,
      ObjectToOffsetMapping mapping,
      LensCodeRewriterUtils rewriter) {
    writeFirst(AA, dest);
    write16BitValue(BBBB, dest);
  }

  @Override
  public final int hashCode() {
    return ((BBBB << 8) | AA) ^ getClass().hashCode();
  }

  @Override
  final int internalCompareTo(Instruction other) {
    Format21t o = (Format21t) other;
    return ComparatorUtils.compareInts(AA, o.AA, BBBB, o.BBBB);
  }

  public abstract Type getType();

  protected abstract ValueTypeConstraint getOperandTypeConstraint();

  @Override
  public int[] getTargets() {
    return new int[]{BBBB, getSize()};
  }

  @Override
  public void buildIR(IRBuilder builder) {
    int offset = getOffset();
    int size = getSize();
    builder.addIfZero(getType(), getOperandTypeConstraint(), AA, offset + BBBB, offset + size);
  }

  @Override
  public String toString(ClassNameMapper naming) {
    return formatString("v" + AA + ", " + formatRelativeOffset(BBBB));
  }

  @Override
  public String toSmaliString(ClassNameMapper naming) {
    return formatSmaliString("v" + AA + ", :label_" + (getOffset() + BBBB));
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
