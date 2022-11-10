// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.IndexedDexItem;
import com.android.tools.r8.utils.RetracerForCodePrinting;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import java.util.function.BiPredicate;

abstract class DexFormat21c<T extends IndexedDexItem> extends DexBase2Format {

  public final short AA;
  public T BBBB;

  // AA | op | [type|field|string]@BBBB
  DexFormat21c(int high, BytecodeStream stream, T[] map) {
    super(stream);
    AA = (short) high;
    BBBB = map[read16BitValue(stream)];
  }

  protected DexFormat21c(int AA, T BBBB) {
    assert 0 <= AA && AA <= Constants.U8BIT_MAX;
    this.AA = (short) AA;
    this.BBBB = BBBB;
  }

  @Override
  public final int hashCode() {
    return ((BBBB.hashCode() << 8) | AA) ^ getClass().hashCode();
  }

  @SuppressWarnings("unchecked")
  @Override
  final int internalAcceptCompareTo(DexInstruction other, CompareToVisitor visitor) {
    return visitor.visit(
        this,
        (DexFormat21c<T>) other,
        spec -> spec.withInt(i -> i.AA).withSpec(this::internalSubSpecify));
  }

  @Override
  final void internalAcceptHashing(HashingVisitor visitor) {
    visitor.visitInt(AA);
    visitor.visit(this, this::internalSubSpecify);
  }

  abstract void internalSubSpecify(StructuralSpecification<DexFormat21c<T>, ?> spec);

  @Override
  public String toString(RetracerForCodePrinting retracer) {
    return formatString("v" + AA + ", " + retracer.toDescriptor(BBBB));
  }

  @Override
  public String toSmaliString(RetracerForCodePrinting retracer) {
    // TODO(sgjesse): Add support for smali name mapping.
    return formatSmaliString("v" + AA + ", " + BBBB.toSmaliString());
  }

  @Override
  public boolean equals(
      DexInstruction other, BiPredicate<IndexedDexItem, IndexedDexItem> equality) {
    if (other == null || this.getClass() != other.getClass()) {
      return false;
    }
    DexFormat21c<?> o = (DexFormat21c<?>) other;
    return o.AA == AA && equality.test(BBBB, o.BBBB);
  }
}
