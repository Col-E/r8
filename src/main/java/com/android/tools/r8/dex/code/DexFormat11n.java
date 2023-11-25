// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ObjectToOffsetMapping;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.utils.RetracerForCodePrinting;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import java.nio.ShortBuffer;

abstract class DexFormat11n extends DexBase1Format {

  public final byte A, B;

  private static void specify(StructuralSpecification<DexFormat11n, ?> spec) {
    spec.withInt(i -> i.A).withInt(i -> i.B);
  }

  @SuppressWarnings("UnnecessaryParentheses")
  // #+B | vA | op
  /*package*/ DexFormat11n(int high, BytecodeStream stream) {
    super(stream);
    A = (byte) (high & 0xf);
    // Sign extend 4bit value.
    high >>= 4;
    if ((high & Constants.S4BIT_SIGN_MASK) != 0) {
      B = (byte) (~(~high & 0xf));
    } else {
      B = (byte) (high & 0xf);
    }
  }

  /*package*/ DexFormat11n(int A, int B) {
    assert 0 <= A && A <= Constants.U4BIT_MAX;
    assert Constants.S4BIT_MIN <= B && B <= Constants.S4BIT_MAX;
    this.A = (byte) A;
    this.B = (byte) B;
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
  }

  @Override
  public final int hashCode() {
    return ((A << 4) | B) ^ getClass().hashCode();
  }

  @Override
  final int internalAcceptCompareTo(DexInstruction other, CompareToVisitor visitor) {
    return visitor.visit(this, (DexFormat11n) other, DexFormat11n::specify);
  }

  @Override
  final void internalAcceptHashing(HashingVisitor visitor) {
    visitor.visit(this, DexFormat11n::specify);
  }

  @Override
  public String toString(RetracerForCodePrinting retracer) {
    return formatString("v" + A + ", #" + B);
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
