// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.code;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.ObjectToOffsetMapping;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.utils.ComparatorUtils;
import com.android.tools.r8.utils.StringUtils;
import java.nio.ShortBuffer;

public abstract class Format22s extends Base2Format {

  public final byte A;
  public final byte B;
  public final short CCCC;

  // vB | vA | op | #+CCCC
  /*package*/ Format22s(int high, BytecodeStream stream) {
    super(stream);
    A = (byte) (high & 0xf);
    B = (byte) ((high >> 4) & 0xf);
    CCCC = readSigned16BitValue(stream);
  }

  /*package*/ Format22s(int A, int B, int CCCC) {
    assert 0 <= A && A <= Constants.U4BIT_MAX;
    assert 0 <= B && B <= Constants.U4BIT_MAX;
    assert Short.MIN_VALUE <= CCCC && CCCC <= Short.MAX_VALUE;
    this.A = (byte) A;
    this.B = (byte) B;
    this.CCCC = (short) CCCC;
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
    return ((CCCC << 8) | (A << 4) | B) ^ getClass().hashCode();
  }

  @Override
  final int internalCompareTo(Instruction other) {
    Format22s o = (Format22s) other;
    return ComparatorUtils.compareInts(A, o.A, B, o.B, CCCC, o.CCCC);
  }

  @Override
  public String toString(ClassNameMapper naming) {
    return formatString("v" + A + ", v" + B + ", #" + CCCC);
  }

  @Override
  public String toSmaliString(ClassNameMapper naming) {
    return formatSmaliString(
        "v" + A + ", v" + B + ", " + StringUtils.hexString(CCCC, 4) + "  # " + CCCC);
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
