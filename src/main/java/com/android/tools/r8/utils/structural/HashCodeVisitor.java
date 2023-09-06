// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.structural;

import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.utils.structural.StructuralItem.CompareToAccept;
import com.android.tools.r8.utils.structural.StructuralItem.HashingAccept;
import java.util.Arrays;
import java.util.Iterator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

/**
 * Simple hash code implementation.
 *
 * <p>This visitor relies on the specification of hashCode on all object types. Thus it does not
 * have a call-back structure that requires the spec implementation as well as a visitor for the
 * recursive decent. There is also no support for overriding the visitation apart from the usual
 * override of hashCode().
 */
public class HashCodeVisitor<T> extends StructuralSpecification<T, HashCodeVisitor<T>> {

  public static <T extends StructuralItem<T>> int run(T item) {
    return run(item, item.getStructuralMapping());
  }

  public static <T> int run(T item, StructuralMapping<T> visit) {
    HashCodeVisitor<T> visitor = new HashCodeVisitor<>(item);
    visit.apply(visitor);
    return visitor.hashCode;
  }

  private final T item;

  private int hashCode = 0;

  private HashCodeVisitor(T item) {
    this.item = item;
  }

  @Override
  HashCodeVisitor<T> self() {
    return this;
  }

  private HashCodeVisitor<T> amend(int value) {
    // This mirrors the behavior of Objects.hash(values...) / Arrays.hashCode(array).
    hashCode = 31 * hashCode + value;
    return this;
  }

  @Override
  public HashCodeVisitor<T> withAssert(Predicate<T> predicate) {
    assert predicate.test(item);
    return this;
  }

  @Override
  public HashCodeVisitor<T> withBool(Predicate<T> getter) {
    return amend(Boolean.hashCode(getter.test(item)));
  }

  @Override
  public HashCodeVisitor<T> withInt(ToIntFunction<T> getter) {
    return amend(Integer.hashCode(getter.applyAsInt(item)));
  }

  @Override
  public HashCodeVisitor<T> withLong(ToLongFunction<T> getter) {
    return amend(Long.hashCode(getter.applyAsLong(item)));
  }

  @Override
  public HashCodeVisitor<T> withDouble(ToDoubleFunction<T> getter) {
    return amend(Double.hashCode(getter.applyAsDouble(item)));
  }

  @Override
  public HashCodeVisitor<T> withIntArray(Function<T, int[]> getter) {
    return amend(Arrays.hashCode(getter.apply(item)));
  }

  @Override
  public HashCodeVisitor<T> withByteArray(Function<T, byte[]> getter) {
    return amend(Arrays.hashCode(getter.apply(item)));
  }

  @Override
  public HashCodeVisitor<T> withShortArray(Function<T, short[]> getter) {
    return amend(Arrays.hashCode(getter.apply(item)));
  }

  @Override
  protected <S> HashCodeVisitor<T> withConditionalCustomItem(
      Predicate<T> predicate,
      Function<T, S> getter,
      CompareToAccept<S> compare,
      HashingAccept<S> hasher) {
    if (predicate.test(item)) {
      return amend(getter.apply(item).hashCode());
    } else {
      // Use the value 1 for the failing-predicate case such that a different hash is obtained for,
      // eg, {null, null} and {null}.
      return amend(1);
    }
  }

  @Override
  protected <S> HashCodeVisitor<T> withCustomItemIterator(
      Function<T, Iterator<S>> getter, CompareToAccept<S> compare, HashingAccept<S> hasher) {
    Iterator<S> it = getter.apply(item);
    while (it.hasNext()) {
      amend(it.next().hashCode());
    }
    return this;
  }

  @Override
  public HashCodeVisitor<T> withDexReference(Function<T, DexReference> getter) {
    return amend(getter.apply(item).hashCode());
  }
}
