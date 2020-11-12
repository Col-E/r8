// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.structural;

import com.android.tools.r8.utils.structural.StructuralItem.CompareToAccept;
import com.android.tools.r8.utils.structural.StructuralItem.HashingAccept;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

public abstract class StructuralSpecification<T, V extends StructuralSpecification<T, V>> {

  /**
   * Basic specification for visiting an item.
   *
   * <p>This specified the getter for the item as well as all of the methods that are required for
   * visiting. Those coincide with the requirements of Specified.
   *
   * <p>It is preferable to use withStructuralItem.
   */
  @Deprecated
  public final <S> V withCustomItem(
      Function<T, S> getter, CompareToAccept<S> compare, HashingAccept<S> hasher) {
    return withConditionalCustomItem(t -> true, getter, compare, hasher);
  }

  protected abstract <S> V withConditionalCustomItem(
      Predicate<T> predicate,
      Function<T, S> getter,
      CompareToAccept<S> compare,
      HashingAccept<S> hasher);

  /**
   * Specification for a "specified" item.
   *
   * <p>Using this the visiting methods are could based on the implementation of the Specified
   * interface.
   */
  public final <S extends StructuralItem<S>> V withItem(Function<T, S> getter) {
    return withConditionalItem(t -> true, getter);
  }

  final <S extends StructuralItem<S>> V withNullableItem(Function<T, S> getter) {
    return withConditionalItem(s -> getter.apply(s) != null, getter);
  }

  public final <S extends StructuralItem<S>> V withConditionalItem(
      Predicate<T> predicate, Function<T, S> getter) {
    return withConditionalCustomItem(predicate, getter, S::acceptCompareTo, S::acceptHashing);
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
}
