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
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.utils.RetracerForCodePrinting;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import java.nio.ShortBuffer;

public abstract class DexFormat22s extends DexBase2Format {

  public final byte A;
  public final byte B;
  public final short CCCC;

  private static void specify(StructuralSpecification<DexFormat22s, ?> spec) {
    spec.withInt(i -> i.A).withInt(i -> i.B).withInt(i -> i.CCCC);
  }

  // vB | vA | op | #+CCCC
  /*package*/ DexFormat22s(int high, BytecodeStream stream) {
    super(stream);
    A = (byte) (high & 0xf);
    B = (byte) ((high >> 4) & 0xf);
    CCCC = readSigned16BitValue(stream);
  }

  /*package*/ DexFormat22s(int A, int B, int CCCC) {
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
      GraphLens codeLens,
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
  final int internalAcceptCompareTo(DexInstruction other, CompareToVisitor visitor) {
    return visitor.visit(this, (DexFormat22s) other, DexFormat22s::specify);
  }

  @Override
  final void internalAcceptHashing(HashingVisitor visitor) {
    visitor.visit(this, DexFormat22s::specify);
  }

  @Override
  public String toString(RetracerForCodePrinting retracer) {
    return formatString("v" + A + ", v" + B + ", #" + CCCC);
  }

  @Override
  public String toSmaliString(RetracerForCodePrinting retracer) {
    return formatSmaliString(
        "v" + A + ", v" + B + ", " + StringUtils.hexString(CCCC, 4) + "  # " + CCCC);
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
