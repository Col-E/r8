// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.structural;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.structural.StructuralItem.HashingAccept;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

public abstract class HashingVisitor {

  public abstract void visitBool(boolean value);

  public abstract void visitInt(int value);

  public abstract void visitFloat(float value);

  public abstract void visitLong(long value);

  public abstract void visitDouble(double value);

  /** Base for visiting an enumeration of items. */
  protected abstract <S> void visitItemIterator(Iterator<S> it, HashingAccept<S> hashingAccept);

  public final <S extends StructuralItem<S>> void visitItemArray(S[] items) {
    visitItemCollection(Arrays.asList(items));
  }

  public final <S extends StructuralItem<S>> void visitItemCollection(Collection<S> items) {
    visitItemIterator(items.iterator(), S::acceptHashing);
  }

  public abstract void visitJavaString(String string);

  public abstract void visitDexString(DexString string);

  public abstract void visitDexType(DexType type);

  public void visitDexField(DexField field) {
    visit(field, field.getStructuralMapping());
  }

  public void visitDexMethod(DexMethod method) {
    visit(method, method.getStructuralMapping());
  }

  public void visitDexReference(DexReference reference) {
    reference.accept(this::visitDexType, this::visitDexField, this::visitDexMethod);
  }

  public abstract <S> void visit(S item, StructuralMapping<S> accept);
}
