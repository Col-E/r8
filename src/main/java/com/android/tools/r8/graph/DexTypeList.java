// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import static com.android.tools.r8.utils.PredicateUtils.not;

import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.dex.MixedSectionCollection;
import com.android.tools.r8.utils.ArrayUtils;
import com.android.tools.r8.utils.structural.StructuralItem;
import com.android.tools.r8.utils.structural.StructuralMapping;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import com.google.common.collect.Iterators;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class DexTypeList extends DexItem implements Iterable<DexType>, StructuralItem<DexTypeList> {

  private static final DexTypeList theEmptyTypeList = new DexTypeList();

  public final DexType[] values;

  private static void specify(StructuralSpecification<DexTypeList, ?> spec) {
    spec.withItemArray(ts -> ts.values);
  }

  public static DexTypeList empty() {
    return theEmptyTypeList;
  }

  private DexTypeList() {
    this.values = DexType.EMPTY_ARRAY;
  }

  public DexTypeList(DexType[] values) {
    assert values != null && values.length > 0;
    this.values = values;
  }

  public DexTypeList(Collection<DexType> values) {
    this(values.toArray(DexType.EMPTY_ARRAY));
  }

  public static DexTypeList create(DexType[] values) {
    return values.length == 0 ? DexTypeList.empty() : new DexTypeList(values);
  }

  public static DexTypeList create(Collection<DexType> values) {
    return values.isEmpty() ? DexTypeList.empty() : new DexTypeList(values);
  }

  public DexType get(int index) {
    return values[index];
  }

  public DexType[] getBacking() {
    return values;
  }

  public DexTypeList keepIf(Predicate<DexType> predicate) {
    DexType[] filtered = ArrayUtils.filter(values, predicate, DexType.EMPTY_ARRAY);
    if (filtered != values) {
      return DexTypeList.create(filtered);
    }
    return this;
  }

  public DexTypeList map(Function<DexType, DexType> fn) {
    if (isEmpty()) {
      return DexTypeList.empty();
    }
    DexType[] newTypes = ArrayUtils.map(values, fn, DexType.EMPTY_ARRAY);
    return newTypes != values ? create(newTypes) : this;
  }

  public DexTypeList removeIf(Predicate<DexType> predicate) {
    return keepIf(not(predicate));
  }

  @Override
  public DexTypeList self() {
    return this;
  }

  @Override
  public StructuralMapping<DexTypeList> getStructuralMapping() {
    return DexTypeList::specify;
  }

  public boolean contains(DexType type) {
    return ArrayUtils.contains(values, type);
  }

  @Override
  public void forEach(Consumer<? super DexType> consumer) {
    for (DexType value : values) {
      consumer.accept(value);
    }
  }

  public void forEachReverse(Consumer<? super DexType> consumer) {
    for (int i = values.length - 1; i >= 0; i--) {
      consumer.accept(values[i]);
    }
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(values);
  }

  void collectIndexedItems(AppView<?> appView, IndexedItemCollection indexedItems) {
    for (DexType type : values) {
      type.collectIndexedItems(appView, indexedItems);
    }
  }

  @Override
  void collectMixedSectionItems(MixedSectionCollection mixedItems) {
    mixedItems.add(this);
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    return (other instanceof DexTypeList)
        && Arrays.equals(values, ((DexTypeList) other).values);
  }

  public boolean isEmpty() {
    return values.length == 0;
  }

  public int size() {
    return values.length;
  }

  public Stream<DexType> stream() {
    return Stream.of(values);
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    if (values.length > 0) {
      builder.append(values[0]);
      for (int i = 1; i < values.length; i++) {
        builder.append(' ').append(values[i]);
      }
    }
    return builder.toString();
  }

  @Override
  public Iterator<DexType> iterator() {
    return Iterators.forArray(values);
  }

  public DexTypeList getSorted() {
    if (values.length <= 1) {
      return this;
    }

    DexType[] newValues = values.clone();
    Arrays.sort(newValues);
    return new DexTypeList(newValues);
  }
}
