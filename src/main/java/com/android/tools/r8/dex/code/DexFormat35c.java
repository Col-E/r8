// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.IndexedDexItem;
import com.android.tools.r8.utils.RetracerForCodePrinting;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;
import com.android.tools.r8.utils.structural.StructuralItem;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import java.util.function.BiPredicate;

public abstract class DexFormat35c<T extends IndexedDexItem & StructuralItem<T>>
    extends DexBase3Format {

  public final byte A;
  public final byte C;
  public final byte D;
  public final byte E;
  public final byte F;
  public final byte G;
  public T BBBB;

  private static <T extends IndexedDexItem & StructuralItem<T>> void specify(
      StructuralSpecification<DexFormat35c<T>, ?> spec) {
    spec.withInt(i -> i.A)
        .withInt(i -> i.C)
        .withInt(i -> i.D)
        .withInt(i -> i.E)
        .withInt(i -> i.F)
        .withInt(i -> i.G)
        .withItem(i -> i.BBBB);
  }

  // A | G | op | BBBB | F | E | D | C
  DexFormat35c(int high, BytecodeStream stream, T[] map) {
    super(stream);
    G = (byte) (high & 0xf);
    A = (byte) ((high >> 4) & 0xf);
    BBBB = map[read16BitValue(stream)];
    int next = read8BitValue(stream);
    E = (byte) (next & 0xf);
    F = (byte) ((next >> 4) & 0xf);
    next = read8BitValue(stream);
    C = (byte) (next & 0xf);
    D = (byte) ((next >> 4) & 0xf);
  }

  DexFormat35c(int A, T BBBB, int C, int D, int E, int F, int G) {
    assert 0 <= A && A <= Constants.U4BIT_MAX;
    assert 0 <= C && C <= Constants.U4BIT_MAX;
    assert 0 <= D && D <= Constants.U4BIT_MAX;
    assert 0 <= E && E <= Constants.U4BIT_MAX;
    assert 0 <= F && F <= Constants.U4BIT_MAX;
    assert 0 <= G && G <= Constants.U4BIT_MAX;
    this.A = (byte) A;
    this.BBBB = BBBB;
    this.C = (byte) C;
    this.D = (byte) D;
    this.E = (byte) E;
    this.F = (byte) F;
    this.G = (byte) G;
  }

  @Override
  public final int hashCode() {
    return ((BBBB.hashCode() << 24) | (A << 20) | (C << 16) | (D << 12) | (E << 8) | (F << 4) | G)
        ^ getClass().hashCode();
  }

  @SuppressWarnings("unchecked")
  @Override
  final int internalAcceptCompareTo(DexInstruction other, CompareToVisitor visitor) {
    return visitor.visit(this, (DexFormat35c<T>) other, DexFormat35c::specify);
  }

  @Override
  void internalAcceptHashing(HashingVisitor visitor) {
    visitor.visit(this, DexFormat35c::specify);
  }

  private void appendRegisterArguments(StringBuilder builder, String separator) {
    builder.append("{ ");
    int[] values = new int[] {C, D, E, F, G};
    for (int i = 0; i < A; i++) {
      if (i != 0) {
        builder.append(separator);
      }
      builder.append("v").append(values[i]);
    }
    builder.append(" }");
  }

  @Override
  public String toString(RetracerForCodePrinting retracer) {
    StringBuilder builder = new StringBuilder();
    appendRegisterArguments(builder, " ");
    builder.append(" ");
    builder.append(retracer.toDescriptor(BBBB));
    return formatString(builder.toString());
  }

  @Override
  public String toSmaliString(RetracerForCodePrinting retracer) {
    StringBuilder builder = new StringBuilder();
    appendRegisterArguments(builder, ", ");
    builder.append(", ");
    // TODO(sgjesse): Add support for smali name mapping.
    builder.append(BBBB.toSmaliString());
    return formatSmaliString(builder.toString());
  }

  @Override
  public boolean equals(
      DexInstruction other, BiPredicate<IndexedDexItem, IndexedDexItem> equality) {
    if (other == null || (this.getClass() != other.getClass())) {
      return false;
    }
    DexFormat35c o = (DexFormat35c) other;
    return o.A == A
        && o.C == C
        && o.D == D
        && o.E == E
        && o.F == F
        && o.G == G
        && equality.test(BBBB, o.BBBB);
  }
}
