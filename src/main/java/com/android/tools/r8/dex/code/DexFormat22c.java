// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.IndexedDexItem;
import com.android.tools.r8.utils.RetracerForCodePrinting;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import java.util.function.BiPredicate;

public abstract class DexFormat22c<T extends DexReference> extends DexBase2Format {

  public final byte A;
  public final byte B;
  public T CCCC;

  private static void specify(
      StructuralSpecification<DexFormat22c<? extends DexReference>, ?> spec) {
    spec.withInt(i -> i.A).withInt(i -> i.B).withDexReference(i -> i.CCCC);
  }

  // vB | vA | op | [type|field]@CCCC
  /*package*/ DexFormat22c(int high, BytecodeStream stream, T[] map) {
    super(stream);
    A = (byte) (high & 0xf);
    B = (byte) ((high >> 4) & 0xf);
    CCCC = map[read16BitValue(stream)];
  }

  /*package*/ DexFormat22c(int A, int B, T CCCC) {
    assert 0 <= A && A <= Constants.U4BIT_MAX;
    assert 0 <= B && B <= Constants.U4BIT_MAX;
    this.A = (byte) A;
    this.B = (byte) B;
    this.CCCC = CCCC;
  }

  @Override
  public final int hashCode() {
    return ((CCCC.hashCode() << 8) | (A << 4) | B) ^ getClass().hashCode();
  }

  @Override
  final int internalAcceptCompareTo(DexInstruction other, CompareToVisitor visitor) {
    return visitor.visit(this, (DexFormat22c<? extends DexReference>) other, DexFormat22c::specify);
  }

  @Override
  void internalAcceptHashing(HashingVisitor visitor) {
    visitor.visit(
        this,
        (StructuralSpecification<DexFormat22c<? extends DexReference>, ?> spec) -> specify(spec));
  }

  @Override
  public String toString(RetracerForCodePrinting retracer) {
    return formatString("v" + A + ", v" + B + ", " + retracer.toDescriptor(CCCC));
  }

  @Override
  public String toSmaliString(RetracerForCodePrinting retracer) {
    // TODO(sgjesse): Add support for smali name mapping.
    return formatSmaliString("v" + A + ", v" + B + ", " + CCCC.toSmaliString());
  }

  @Override
  public boolean equals(
      DexInstruction other, BiPredicate<IndexedDexItem, IndexedDexItem> equality) {
    if (other == null || this.getClass() != other.getClass()) {
      return false;
    }
    DexFormat22c<?> o = (DexFormat22c<?>) other;
    return o.A == A && o.B == B && equality.test(CCCC, o.CCCC);
  }
}
