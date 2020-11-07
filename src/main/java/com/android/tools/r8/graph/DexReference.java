// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.dex.IndexedItemCollection;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/** A common interface for {@link DexType}, {@link DexField}, and {@link DexMethod}. */
public abstract class DexReference extends IndexedDexItem {

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

  public abstract void collectIndexedItems(IndexedItemCollection indexedItems);

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

  private int referenceTypeOrder() {
    if (isDexType()) {
      return 1;
    }
    if (isDexField()) {
      return 2;
    }
    assert isDexMethod();
    return 3;
  }

  public int referenceCompareTo(DexReference o) {
    int typeDiff = referenceTypeOrder() - o.referenceTypeOrder();
    if (typeDiff != 0) {
      return typeDiff;
    }
    if (isDexType()) {
      return asDexType().slowCompareTo(o.asDexType());
    }
    if (isDexField()) {
      return asDexField().slowCompareTo(o.asDexField());
    }
    assert isDexMethod();
    return asDexMethod().slowCompareTo(o.asDexMethod());
  }
}
