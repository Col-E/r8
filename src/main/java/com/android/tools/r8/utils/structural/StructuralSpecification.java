// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.structural;

import com.android.tools.r8.utils.structural.StructuralItem.CompareToAccept;
import com.android.tools.r8.utils.structural.StructuralItem.HashingAccept;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

public abstract class StructuralSpecification<T, V extends StructuralSpecification<T, V>> {

  /**
   * Base for accessing and visiting a sub-part on an item.
   *
   * <p>This specifies the getter for the sub-part as well as all of the methods that are required
   * for visiting. The required methods coincide with the requirements of StructuralItem.
   *
   * <p>It is preferable to use withItem and make the item itself implement StructuralItem.
   */
  @Deprecated
  public final <S> V withCustomItem(
      Function<T, S> getter, CompareToAccept<S> compare, HashingAccept<S> hasher) {
    return withConditionalCustomItem(t -> true, getter, compare, hasher);
  }

  public final <S> V withCustomItem(Function<T, S> getter, StructuralAcceptor<S> acceptor) {
    return withCustomItem(getter, acceptor, acceptor);
  }

  /** Base implementation for visiting an item. */
  protected abstract <S> V withConditionalCustomItem(
      Predicate<T> predicate,
      Function<T, S> getter,
      CompareToAccept<S> compare,
      HashingAccept<S> hasher);

  /** Base implementation for visiting an enumeration of items. */
  protected abstract <S> V withItemIterator(
      Function<T, Iterator<S>> getter, CompareToAccept<S> compare, HashingAccept<S> hasher);

  public final <S> V withCustomItemCollection(
      Function<T, Collection<S>> getter, StructuralAcceptor<S> acceptor) {
    return withItemIterator(getter.andThen(Collection::iterator), acceptor, acceptor);
  }

  /**
   * Specification for a "specified" item.
   *
   * <p>Using this the visiting methods are could based on the implementation of the Specified
   * interface.
   */
  public final <S extends StructuralItem<S>> V withItem(Function<T, S> getter) {
    return withConditionalItem(t -> true, getter);
  }

  public final <S extends StructuralItem<S>> V withNullableItem(Function<T, S> getter) {
    return withConditionalItem(s -> getter.apply(s) != null, getter);
  }

  public final <S extends StructuralItem<S>> V withConditionalItem(
      Predicate<T> predicate, Function<T, S> getter) {
    return withConditionalCustomItem(
        predicate, getter, StructuralItem::acceptCompareTo, StructuralItem::acceptHashing);
  }

  public final <S extends StructuralItem<S>> V withItemCollection(
      Function<T, Collection<S>> getter) {
    return withItemIterator(
        getter.andThen(Collection::iterator),
        StructuralItem::acceptCompareTo,
        StructuralItem::acceptHashing);
  }

  public final <S extends StructuralItem<S>> V withItemArray(Function<T, S[]> getter) {
    return withItemIterator(
        getter.andThen(a -> Arrays.asList(a).iterator()),
        StructuralItem::acceptCompareTo,
        StructuralItem::acceptHashing);
  }

  /**
   * Helper to declare an assert on the item.
   *
   * <p>Only run if running with -ea. Must be run on any item being visited (ie, both in the case of
   * comparisons and equality).
   */
  public abstract V withAssert(Predicate<T> predicate);

  // Primitive Java types. These will need overriding to avoid boxing.
  public abstract V withBool(Predicate<T> getter);

  public abstract V withInt(ToIntFunction<T> getter);

  public abstract V withLong(ToLongFunction<T> getter);

  public abstract V withDouble(ToDoubleFunction<T> getter);

  public abstract V withIntArray(Function<T, int[]> getter);
}
