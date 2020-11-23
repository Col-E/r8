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

  public abstract int visitBool(boolean value1, boolean value2);

  public abstract int visitInt(int value1, int value2);

  public abstract int visitLong(long value1, long value2);

  public abstract int visitFloat(float value1, float value2);

  public abstract int visitDouble(double value1, double value2);

  /** Base for visiting an enumeration of items. */
  public abstract <S> int visitItemIterator(
      Iterator<S> it1, Iterator<S> it2, CompareToAccept<S> compareToAccept);

  public final <S extends StructuralItem<S>> int visitItemArray(S[] items1, S[] items2) {
    return visitItemCollection(Arrays.asList(items1), Arrays.asList(items2));
  }

  public final <S extends StructuralItem<S>> int visitItemCollection(
      Collection<S> items1, Collection<S> items2) {
    return visitItemIterator(
        items1.iterator(),
        items2.iterator(),
        (s, other, visitor) -> s.acceptCompareTo(other, visitor));
  }

  public abstract int visitDexString(DexString string1, DexString string2);

  public abstract int visitDexType(DexType type1, DexType type2);

  public int visitDexField(DexField field1, DexField field2) {
    return visit(field1, field2, field1.getStructuralAccept());
  }

  public int visitDexMethod(DexMethod method1, DexMethod method2) {
    return visit(method1, method2, method1.getStructuralAccept());
  }

  public abstract int visitDexReference(DexReference reference1, DexReference reference2);

  public abstract <S> int visit(S item1, S item2, StructuralAccept<S> accept);

  @Deprecated
  public abstract <S> int visit(S item1, S item2, Comparator<S> comparator);
}
