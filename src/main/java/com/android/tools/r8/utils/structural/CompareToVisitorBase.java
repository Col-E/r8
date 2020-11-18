// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.structural;

import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.utils.structural.StructuralItem.CompareToAccept;
import com.android.tools.r8.utils.structural.StructuralItem.HashingAccept;
import java.util.Comparator;
import java.util.Iterator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/** Base class to share most visiting methods */
public abstract class CompareToVisitorBase extends CompareToVisitor {

  private int order = 0;

  public final boolean stillEqual() {
    return order == 0;
  }

  public final int getOrder() {
    return order;
  }

  public final void setOrder(int order) {
    this.order = order;
  }

  @Override
  public final void visitBool(boolean value1, boolean value2) {
    if (stillEqual()) {
      setOrder(Boolean.compare(value1, value2));
    }
  }

  @Override
  public final void visitInt(int value1, int value2) {
    if (stillEqual()) {
      setOrder(Integer.compare(value1, value2));
    }
  }

  @Override
  public void visitLong(long value1, long value2) {
    if (stillEqual()) {
      setOrder(Long.compare(value1, value2));
    }
  }

  @Override
  public void visitFloat(float value1, float value2) {
    if (stillEqual()) {
      setOrder(Float.compare(value1, value2));
    }
  }

  @Override
  public void visitDouble(double value1, double value2) {
    if (stillEqual()) {
      setOrder(Double.compare(value1, value2));
    }
  }

  @Override
  protected <S> void visitItemIterator(
      Iterator<S> it1, Iterator<S> it2, CompareToAccept<S> compareToAccept) {
    while (stillEqual() && it1.hasNext() && it2.hasNext()) {
      compareToAccept.accept(it1.next(), it2.next(), this);
    }
    if (stillEqual()) {
      visitBool(it1.hasNext(), it2.hasNext());
    }
  }

  @Override
  public final void visitDexString(
      DexString string1, DexString string2, Comparator<DexString> comparator) {
    if (stillEqual()) {
      setOrder(comparator.compare(string1, string2));
    }
  }

  @Override
  public void visitDexReference(DexReference reference1, DexReference reference2) {
    if (stillEqual()) {
      visitInt(reference1.referenceTypeOrder(), reference2.referenceTypeOrder());
      if (stillEqual()) {
        assert reference1.getClass() == reference2.getClass();
        if (reference1.isDexType()) {
          visitDexType(reference1.asDexType(), reference2.asDexType());
        } else if (reference1.isDexField()) {
          visitDexField(reference1.asDexField(), reference2.asDexField());
        } else {
          visitDexMethod(reference1.asDexMethod(), reference2.asDexMethod());
        }
      }
    }
  }

  @Override
  public final <S> void visit(S item1, S item2, Comparator<S> comparator) {
    if (stillEqual()) {
      setOrder(comparator.compare(item1, item2));
    }
  }

  @Override
  public final <S> void visit(S item1, S item2, StructuralAccept<S> accept) {
    if (stillEqual()) {
      accept.accept(new ItemSpecification<>(item1, item2, this));
    }
  }

  private static class ItemSpecification<T>
      extends StructuralSpecification<T, ItemSpecification<T>> {

    private final CompareToVisitorBase parent;
    private final T item1;
    private final T item2;

    private ItemSpecification(T item1, T item2, CompareToVisitorBase parent) {
      this.item1 = item1;
      this.item2 = item2;
      this.parent = parent;
    }

    @Override
    public ItemSpecification<T> withAssert(Predicate<T> predicate) {
      assert predicate.test(item1);
      assert predicate.test(item2);
      return this;
    }

    @Override
    public ItemSpecification<T> withBool(Predicate<T> getter) {
      parent.visitBool(getter.test(item1), getter.test(item2));
      return this;
    }

    @Override
    public ItemSpecification<T> withInt(ToIntFunction<T> getter) {
      parent.visitInt(getter.applyAsInt(item1), getter.applyAsInt(item2));
      return this;
    }

    @Override
    public <S> ItemSpecification<T> withConditionalCustomItem(
        Predicate<T> predicate,
        Function<T, S> getter,
        CompareToAccept<S> compare,
        HashingAccept<S> hasher) {
      if (parent.stillEqual()) {
        boolean test1 = predicate.test(item1);
        boolean test2 = predicate.test(item2);
        if (test1 && test2) {
          compare.accept(getter.apply(item1), getter.apply(item2), parent);
        } else {
          parent.visitBool(test1, test2);
        }
      }
      return this;
    }

    @Override
    protected <S> ItemSpecification<T> withItemIterator(
        Function<T, Iterator<S>> getter, CompareToAccept<S> compare, HashingAccept<S> hasher) {
      if (parent.stillEqual()) {
        parent.visitItemIterator(getter.apply(item1), getter.apply(item2), compare);
      }
      return this;
    }
  }
}
