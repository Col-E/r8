// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.structural;

import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.utils.ComparatorUtils;
import com.android.tools.r8.utils.structural.StructuralItem.CompareToAccept;
import com.android.tools.r8.utils.structural.StructuralItem.HashingAccept;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

public abstract class StructuralSpecification<T, V extends StructuralSpecification<T, V>> {

  abstract V self();

  /** Apply a structural mapping to the present specification. */
  public final V withSpec(StructuralMapping<T> spec) {
    spec.apply(this);
    return self();
  }

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
  protected abstract <S> V withCustomItemIterator(
      Function<T, Iterator<S>> getter, CompareToAccept<S> compare, HashingAccept<S> hasher);

  public final <S> V withCustomItemCollection(
      Function<T, Collection<S>> getter, StructuralAcceptor<S> acceptor) {
    return withCustomItemIterator(getter.andThen(Collection::iterator), acceptor, acceptor);
  }

  public final <S> V withCustomItemArray(Function<T, S[]> getter, StructuralAcceptor<S> acceptor) {
    return withCustomItemIterator(
        getter.andThen(a -> Arrays.asList(a).iterator()), acceptor, acceptor);
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

  public final <S extends StructuralItem<S>> V withItemIterator(Function<T, Iterator<S>> getter) {
    return withCustomItemIterator(
        getter, StructuralItem::acceptCompareTo, StructuralItem::acceptHashing);
  }

  public final <S extends StructuralItem<S>> V withItemCollection(
      Function<T, Collection<S>> getter) {
    return withItemIterator(getter.andThen(Collection::iterator));
  }

  public final <S extends StructuralItem<S>> V withItemArray(Function<T, S[]> getter) {
    return withItemIterator(getter.andThen(a -> Arrays.asList(a).iterator()));
  }

  public final <S extends StructuralItem<S>> V withItemArrayAllowingNullMembers(
      Function<T, S[]> getter) {
    return withCustomItemIterator(
        getter.andThen(a -> Arrays.asList(a).iterator()),
        (a, b, visitor) -> {
          if (a == null || b == null) {
            return visitor.visitBool(a != null, b != null);
          }
          return a.acceptCompareTo(b, visitor);
        },
        (a, visitor) -> {
          if (a == null) {
            visitor.visitInt(0);
          } else {
            a.acceptHashing(visitor);
          }
        });
  }

  private <S> V withInt2CustomItemMap(
      Function<T, Int2ReferenceMap<S>> getter,
      CompareToAccept<S> compare,
      HashingAccept<S> hasher) {
    return withCustomItem(
        getter,
        new StructuralAcceptor<>() {
          @Override
          public int acceptCompareTo(
              Int2ReferenceMap<S> map1, Int2ReferenceMap<S> map2, CompareToVisitor visitor) {
            return ComparatorUtils.compareInt2ReferenceMap(
                map1, map2, (s1, s2) -> compare.acceptCompareTo(s1, s2, visitor));
          }

          @Override
          public void acceptHashing(Int2ReferenceMap<S> map, HashingVisitor visitor) {
            // We might want to optimize this to avoid sorting. Potentiality compute the min-max
            // range and use a fori, or collect some number of the smallest keys and hash just
            // those.
            ArrayList<Integer> keys = new ArrayList<>(map.keySet());
            keys.sort(Integer::compareTo);
            for (int key : keys) {
              visitor.visitInt(key);
              hasher.acceptHashing(map.get(key), visitor);
            }
          }
        });
  }

  public final <S> V withInt2CustomItemMap(
      Function<T, Int2ReferenceMap<S>> getter, StructuralAcceptor<S> acceptor) {
    return withInt2CustomItemMap(getter, acceptor, acceptor);
  }

  public final <S extends StructuralItem<S>> V withInt2ItemMap(
      Function<T, Int2ReferenceMap<S>> getter) {
    return withInt2CustomItemMap(getter, S::acceptCompareTo, S::acceptHashing);
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

  public abstract V withByteArray(Function<T, byte[]> getter);

  public abstract V withShortArray(Function<T, short[]> getter);

  public abstract V withDexReference(Function<T, DexReference> getter);
}
