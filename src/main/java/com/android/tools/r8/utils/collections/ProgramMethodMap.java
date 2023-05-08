// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.utils.ProgramMethodEquivalence;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class ProgramMethodMap<V> extends DexClassAndMemberMap<ProgramMethod, V> {

  private static final ProgramMethodMap<?> EMPTY = new ProgramMethodMap<>(ImmutableMap::of);

  private ProgramMethodMap(Supplier<Map<Wrapper<ProgramMethod>, V>> backingFactory) {
    super(backingFactory);
  }

  private ProgramMethodMap(Map<Wrapper<ProgramMethod>, V> backing) {
    super(backing);
  }

  public static <V> ProgramMethodMap<V> create() {
    return new ProgramMethodMap<>(HashMap::new);
  }

  public static <V> ProgramMethodMap<V> create(int capacity) {
    return new ProgramMethodMap<>(new HashMap<>(capacity));
  }

  public static <V> ProgramMethodMap<V> createConcurrent() {
    return new ProgramMethodMap<>(ConcurrentHashMap::new);
  }

  @SuppressWarnings("unchecked")
  public static <V> ProgramMethodMap<V> empty() {
    return (ProgramMethodMap<V>) EMPTY;
  }

  @Override
  Wrapper<ProgramMethod> wrap(ProgramMethod method) {
    return ProgramMethodEquivalence.get().wrap(method);
  }
}
