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

public abstract class DexFormat3rc<T extends IndexedDexItem & StructuralItem<T>>
    extends DexBase3Format {

  public final short AA;
  public final char CCCC;
  public T BBBB;

  private static <T extends IndexedDexItem & StructuralItem<T>> void specify(
      StructuralSpecification<DexFormat3rc<T>, ?> spec) {
    spec.withInt(i -> i.AA).withInt(i -> i.CCCC).withItem(i -> i.BBBB);
  }

  // AA | op | [meth|type]@BBBBB | CCCC
  DexFormat3rc(int high, BytecodeStream stream, T[] map) {
    super(stream);
    this.AA = (short) high;
    this.BBBB = map[read16BitValue(stream)];
    this.CCCC = read16BitValue(stream);
  }

  DexFormat3rc(int firstArgumentRegister, int argumentCount, T dexItem) {
    assert 0 <= firstArgumentRegister && firstArgumentRegister <= Constants.U16BIT_MAX;
    assert 0 <= argumentCount && argumentCount <= Constants.U8BIT_MAX;
    this.CCCC = (char) firstArgumentRegister;
    this.AA = (short) argumentCount;
    BBBB = dexItem;
  }

  public T getItem() {
    return BBBB;
  }

  @Override
  public final int hashCode() {
    return ((CCCC << 24) | (BBBB.hashCode() << 4) | AA) ^ getClass().hashCode();
  }

  @SuppressWarnings("unchecked")
  @Override
  final int internalAcceptCompareTo(DexInstruction other, CompareToVisitor visitor) {
    return visitor.visit(this, (DexFormat3rc<T>) other, DexFormat3rc::specify);
  }

  @Override
  final void internalAcceptHashing(HashingVisitor visitor) {
    visitor.visit(this, DexFormat3rc::specify);
  }

  private void appendRegisterRange(StringBuilder builder) {
    int firstRegister = CCCC;
    builder.append("{ ");
    builder.append("v").append(firstRegister);
    if (AA != 1) {
      builder.append(" .. v").append(firstRegister + AA - 1);
    }
    builder.append(" }");
  }

  @Override
  public String toString(RetracerForCodePrinting retracer) {
    StringBuilder builder = new StringBuilder();
    appendRegisterRange(builder);
    builder.append(" ");
    builder.append(retracer.toDescriptor(BBBB));
    return formatString(builder.toString());
  }

  @Override
  public String toSmaliString(RetracerForCodePrinting retracer) {
    StringBuilder builder = new StringBuilder();
    appendRegisterRange(builder);
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
    DexFormat3rc<?> o = (DexFormat3rc<?>) other;
    return o.AA == AA && o.CCCC == CCCC && equality.test(BBBB, o.BBBB);
  }
}
