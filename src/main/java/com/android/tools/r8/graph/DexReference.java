// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * A common interface for {@link DexType}, {@link DexField}, and {@link DexMethod}.
 */
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

  public static Stream<DexReference> filterDexReference(Stream<DexItem> stream) {
    return DexItem.filter(stream, DexReference.class);
  }

  private static <T extends DexReference> Stream<T> filter(
      Stream<DexReference> stream,
      Predicate<DexReference> pred,
      Function<DexReference, T> f) {
    return stream.filter(pred).map(f);
  }

  public static Stream<DexType> filterDexType(Stream<DexReference> stream) {
    return filter(stream, DexReference::isDexType, DexReference::asDexType);
  }

  public static Stream<DexField> filterDexField(Stream<DexReference> stream) {
    return filter(stream, DexReference::isDexField, DexReference::asDexField);
  }

  public static Stream<DexMethod> filterDexMethod(Stream<DexReference> stream) {
    return filter(stream, DexReference::isDexMethod, DexReference::asDexMethod);
  }
}
