// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.lightir.LirConstant;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/** A common interface for {@link DexType}, {@link DexField}, and {@link DexMethod}. */
public abstract class DexReference extends IndexedDexItem implements LirConstant {

  @SuppressWarnings("ReferenceEquality")
  public static boolean identical(DexReference t1, DexReference t2) {
    return t1 == t2;
  }

  public abstract <T> T apply(
      Function<DexType, T> classConsumer,
      Function<DexField, T> fieldConsumer,
      Function<DexMethod, T> methodConsumer);

  public abstract void accept(
      Consumer<DexType> classConsumer,
      Consumer<DexField> fieldConsumer,
      Consumer<DexMethod> methodConsumer);

  public abstract <T> void accept(
      BiConsumer<DexType, T> classConsumer,
      BiConsumer<DexField, T> fieldConsumer,
      BiConsumer<DexMethod, T> methodConsumer,
      T arg);

  @SuppressWarnings("MissingImplementsComparable")
  public static <R extends DexReference, T> T applyPair(
      R one,
      R other,
      BiFunction<DexType, DexType, T> classConsumer,
      BiFunction<DexField, DexField, T> fieldConsumer,
      BiFunction<DexMethod, DexMethod, T> methodConsumer) {
    if (one.isDexType()) {
      return classConsumer.apply(one.asDexType(), other.asDexType());
    } else if (one.isDexField()) {
      return fieldConsumer.apply(one.asDexField(), other.asDexField());
    } else if (one.isDexMethod()) {
      return methodConsumer.apply(one.asDexMethod(), other.asDexMethod());
    }
    throw new Unreachable();
  }

  public abstract void collectIndexedItems(AppView<?> appView, IndexedItemCollection indexedItems);

  @SuppressWarnings("MissingImplementsComparable")
  public abstract int compareTo(DexReference other);

  public abstract DexType getContextType();

  public boolean isDexType() {
    return false;
  }

  public DexType asDexType() {
    return null;
  }

  public boolean isDexMember() {
    return false;
  }

  public DexMember<?, ?> asDexMember() {
    return null;
  }

  public boolean isDexField() {
    return false;
  }

  public DexField asDexField() {
    return null;
  }

  public boolean isDexMethod() {
    return false;
  }

  public DexMethod asDexMethod() {
    return null;
  }

  public boolean isSamePackage(DexReference reference) {
    return getContextType().isSamePackage(reference.getContextType());
  }

  public int referenceTypeOrder() {
    if (isDexType()) {
      return 1;
    }
    if (isDexField()) {
      return 2;
    }
    assert isDexMethod();
    return 3;
  }
}
