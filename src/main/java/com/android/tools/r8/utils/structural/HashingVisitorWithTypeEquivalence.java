// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.structural;

import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.structural.StructuralItem.CompareToAccept;
import com.android.tools.r8.utils.structural.StructuralItem.HashingAccept;
import com.google.common.hash.Hasher;
import java.util.Iterator;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/** Visitor for hashing a structural item under some assumed type equivalence. */
public class HashingVisitorWithTypeEquivalence extends HashingVisitor {

  public static <T> void run(
      T item, Hasher hasher, RepresentativeMap map, StructuralAccept<T> accept) {
    run(item, hasher, map, (i, visitor) -> visitor.visit(i, accept));
  }

  public static <T> void run(
      T item, Hasher hasher, RepresentativeMap map, HashingAccept<T> hashingAccept) {
    hashingAccept.accept(item, new HashingVisitorWithTypeEquivalence(hasher, map));
  }

  private final Hasher hash;
  private final RepresentativeMap representatives;

  private HashingVisitorWithTypeEquivalence(Hasher hash, RepresentativeMap representatives) {
    this.hash = hash;
    this.representatives = representatives;
  }

  @Override
  public void visitBool(boolean value) {
    hash.putBoolean(value);
  }

  @Override
  public void visitInt(int value) {
    hash.putInt(value);
  }

  @Override
  public void visitFloat(float value) {
    hash.putFloat(value);
  }

  @Override
  public void visitLong(long value) {
    hash.putLong(value);
  }

  @Override
  public void visitDouble(double value) {
    hash.putDouble(value);
  }

  @Override
  public void visitDexString(DexString string) {
    visitInt(string.hashCode());
  }

  @Override
  public void visitDexType(DexType type) {
    visitDexString(representatives.getRepresentative(type).getDescriptor());
  }

  @Override
  public <S> void visit(S item, StructuralAccept<S> accept) {
    accept.accept(new ItemSpecification<>(item, this));
  }

  @Override
  protected <S> void visitItemIterator(Iterator<S> it, HashingAccept<S> hashingAccept) {
    while (it.hasNext()) {
      hashingAccept.accept(it.next(), this);
    }
  }

  @Override
  public <S> void visit(S item, BiConsumer<S, Hasher> hasher) {
    hasher.accept(item, hash);
  }

  private static class ItemSpecification<T>
      extends StructuralSpecification<T, ItemSpecification<T>> {

    private final HashingVisitorWithTypeEquivalence parent;
    private final T item;

    private ItemSpecification(T item, HashingVisitorWithTypeEquivalence parent) {
      this.item = item;
      this.parent = parent;
    }

    @Override
    public ItemSpecification<T> withAssert(Predicate<T> predicate) {
      assert predicate.test(item);
      return this;
    }

    @Override
    public ItemSpecification<T> withBool(Predicate<T> getter) {
      parent.visitBool(getter.test(item));
      return this;
    }

    @Override
    public ItemSpecification<T> withInt(ToIntFunction<T> getter) {
      parent.visitInt(getter.applyAsInt(item));
      return this;
    }

    @Override
    protected <S> ItemSpecification<T> withConditionalCustomItem(
        Predicate<T> predicate,
        Function<T, S> getter,
        CompareToAccept<S> compare,
        HashingAccept<S> hasher) {
      boolean test = predicate.test(item);
      // Always hash the predicate result to distinguish, eg, {null, null} and {null}.
      parent.visitBool(test);
      if (test) {
        hasher.accept(getter.apply(item), parent);
      }
      return this;
    }

    @Override
    protected <S> ItemSpecification<T> withItemIterator(
        Function<T, Iterator<S>> getter, CompareToAccept<S> compare, HashingAccept<S> hasher) {
      parent.visitItemIterator(getter.apply(item), hasher);
      return this;
    }
  }
}
