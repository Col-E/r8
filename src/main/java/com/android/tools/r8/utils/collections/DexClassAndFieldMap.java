// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import com.android.tools.r8.graph.DexClassAndField;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class DexClassAndFieldMap<V> extends DexClassAndFieldMapBase<DexClassAndField, V> {

  private static final DexClassAndFieldMap<?> EMPTY = new DexClassAndFieldMap<>(ImmutableMap::of);

  private DexClassAndFieldMap(Supplier<Map<Wrapper<DexClassAndField>, V>> backingFactory) {
    super(backingFactory);
  }

  public static <V> DexClassAndFieldMap<V> create() {
    return new DexClassAndFieldMap<>(HashMap::new);
  }

  @SuppressWarnings("unchecked")
  public static <V> DexClassAndFieldMap<V> empty() {
    return (DexClassAndFieldMap<V>) EMPTY;
  }
}
