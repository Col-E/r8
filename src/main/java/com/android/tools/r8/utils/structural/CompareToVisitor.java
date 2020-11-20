// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.structural;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.structural.StructuralItem.CompareToAccept;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;

/** Base class for a visitor implementing compareTo on a structural item. */
public abstract class CompareToVisitor {

  public abstract void visitBool(boolean value1, boolean value2);

  public abstract void visitInt(int value1, int value2);

  public abstract void visitLong(long value1, long value2);

  public abstract void visitFloat(float value1, float value2);

  public abstract void visitDouble(double value1, double value2);

  /** Base for visiting an enumeration of items. */
  public abstract <S> void visitItemIterator(
      Iterator<S> it1, Iterator<S> it2, CompareToAccept<S> compareToAccept);

  public final <S extends StructuralItem<S>> void visitItemArray(S[] items1, S[] items2) {
    visitItemCollection(Arrays.asList(items1), Arrays.asList(items2));
  }

  public final <S extends StructuralItem<S>> void visitItemCollection(
      Collection<S> items1, Collection<S> items2) {
    visitItemIterator(items1.iterator(), items2.iterator(), S::acceptCompareTo);
  }

  public abstract void visitDexString(DexString string1, DexString string2);

  public abstract void visitDexType(DexType type1, DexType type2);

  public void visitDexField(DexField field1, DexField field2) {
    visit(field1, field2, field1.getStructuralAccept());
  }

  public void visitDexMethod(DexMethod method1, DexMethod method2) {
    visit(method1, method2, method1.getStructuralAccept());
  }

  public abstract void visitDexReference(DexReference reference1, DexReference reference2);

  public abstract <S> void visit(S item1, S item2, StructuralAccept<S> accept);

  @Deprecated
  public abstract <S> void visit(S item1, S item2, Comparator<S> comparator);
}
