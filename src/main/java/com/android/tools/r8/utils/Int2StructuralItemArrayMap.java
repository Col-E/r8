// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.utils.structural.StructuralItem;
import com.android.tools.r8.utils.structural.StructuralMapping;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

public class Int2StructuralItemArrayMap<T extends StructuralItem<T>>
    implements StructuralItem<Int2StructuralItemArrayMap<T>> {

  private final int[] keys;
  private final List<T> values;

  private Int2StructuralItemArrayMap(int[] keys, List<T> values) {
    this.keys = keys;
    this.values = values;
    assert keys.length == values.size();
  }

  @Override
  public Int2StructuralItemArrayMap<T> self() {
    return this;
  }

  @Override
  public StructuralMapping<Int2StructuralItemArrayMap<T>> getStructuralMapping() {
    return Int2StructuralItemArrayMap::specify;
  }

  private static <T extends StructuralItem<T>> void specify(
      StructuralSpecification<Int2StructuralItemArrayMap<T>, ?> spec) {
    spec.withIntArray(p -> p.keys).withItemCollection(p -> p.values);
  }

  public T lookup(int key) {
    for (int i = 0; i < keys.length; i++) {
      if (keys[i] == key) {
        return values.get(i);
      }
    }
    return null;
  }

  public void forEach(BiConsumer<Integer, T> visitor) {
    for (int i = 0; i < keys.length; i++) {
      visitor.accept(keys[i], values.get(i));
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public boolean equals(Object other) {
    return other instanceof Int2StructuralItemArrayMap
        && compareTo((Int2StructuralItemArrayMap<T>) other) == 0;
  }

  @Override
  public int hashCode() {
    return Objects.hash(Arrays.hashCode(keys), values);
  }

  public static <T extends StructuralItem<T>> Builder<T> builder() {
    return new Builder<T>();
  }

  public boolean isEmpty() {
    return keys.length == 0;
  }

  public static class Builder<T extends StructuralItem<T>> {

    private final List<Integer> keys = new ArrayList<>();
    private final ImmutableList.Builder<T> values = ImmutableList.builder();

    private Builder() {}

    public Builder<T> put(int key, T value) {
      keys.add(key);
      values.add(value);
      return this;
    }

    public Int2StructuralItemArrayMap<T> build() {
      return new Int2StructuralItemArrayMap<T>(Ints.toArray(keys), values.build());
    }
  }
}
