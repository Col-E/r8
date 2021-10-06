// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.structural;

/** Specified types must implement methods to determine equality, hashing and order. */
public interface StructuralItem<T extends StructuralItem<T>> extends Ordered<T> {

  T self();

  StructuralMapping<T> getStructuralMapping();

  // CompareTo implementation and callbacks.

  @FunctionalInterface
  interface CompareToAccept<T> {
    int acceptCompareTo(T item1, T item2, CompareToVisitor visitor);
  }

  /**
   * Implementation of the default compareTo on the item.
   *
   * <p>This should *not* be overwritten, instead items should overwrite acceptCompareTo which will
   * ensure that the effect is in place for any CompareToVisitor.
   */
  @Override
  default int compareTo(T other) {
    return DefaultCompareToVisitor.run(self(), other, StructuralItem::acceptCompareTo);
  }

  /**
   * Implementation of a compareTo with a type equivalence on an item.
   *
   * <p>This should *not* be overwritten, instead items should overwrite acceptCompareTo which will
   * ensure that the effect is in place for any CompareToVisitor.
   */
  default int compareWithTypeEquivalenceTo(T other, RepresentativeMap map) {
    return CompareToVisitorWithTypeEquivalence.run(
        self(), other, map, StructuralItem::acceptCompareTo);
  }

  /** Default accept for compareTo visitors. Override to change behavior. */
  default int acceptCompareTo(T other, CompareToVisitor visitor) {
    return visitor.visit(self(), other, self().getStructuralMapping());
  }

  // Hashing implemenation and callbacks.

  @FunctionalInterface
  interface HashingAccept<T> {
    void acceptHashing(T item, HashingVisitor visitor);
  }

  /**
   * Implementation of the default hashing of an item.
   *
   * <p>This should *not* be overwritten, instead items should overwrite acceptHashing which will
   * ensure that the effect is in place for any HashingVisitor.
   */
  default void hash(HasherWrapper hasher) {
    DefaultHashingVisitor.run(self(), hasher, StructuralItem::acceptHashing);
  }

  /**
   * Implementation of the default hashing with a type equivalence on the item.
   *
   * <p>This should *not* be overwritten, instead items should overwrite acceptHashing which will
   * ensure that the effect is in place for any HashingVisitor.
   */
  default void hashWithTypeEquivalence(HasherWrapper hasher, RepresentativeMap map) {
    HashingVisitorWithTypeEquivalence.run(self(), hasher, map, StructuralItem::acceptHashing);
  }

  /** Default accept for hashing visitors. Override to change behavior. */
  default void acceptHashing(HashingVisitor visitor) {
    visitor.visit(self(), self().getStructuralMapping());
  }
}
