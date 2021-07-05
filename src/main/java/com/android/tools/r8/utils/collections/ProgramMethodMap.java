// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.utils.ProgramMethodEquivalence;
import com.google.common.base.Equivalence.Wrapper;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;

public class ProgramMethodMap<V> {

  private final Map<Wrapper<ProgramMethod>, V> backing;

  private ProgramMethodMap(Supplier<Map<Wrapper<ProgramMethod>, V>> backingFactory) {
    backing = backingFactory.get();
  }

  public static <V> ProgramMethodMap<V> create() {
    return new ProgramMethodMap<>(HashMap::new);
  }

  public static <V> ProgramMethodMap<V> createConcurrent() {
    return new ProgramMethodMap<>(ConcurrentHashMap::new);
  }

  public static <V> ProgramMethodMap<V> createLinked() {
    return new ProgramMethodMap<>(LinkedHashMap::new);
  }

  public void clear() {
    backing.clear();
  }

  public V computeIfAbsent(ProgramMethod method, Function<ProgramMethod, V> fn) {
    return backing.computeIfAbsent(wrap(method), key -> fn.apply(key.get()));
  }

  public void forEach(BiConsumer<ProgramMethod, V> consumer) {
    backing.forEach((wrapper, value) -> consumer.accept(wrapper.get(), value));
  }

  public boolean isEmpty() {
    return backing.isEmpty();
  }

  public V put(ProgramMethod method, V value) {
    Wrapper<ProgramMethod> wrapper = ProgramMethodEquivalence.get().wrap(method);
    return backing.put(wrapper, value);
  }

  public void removeIf(BiPredicate<ProgramMethod, V> predicate) {
    backing.entrySet().removeIf(entry -> predicate.test(entry.getKey().get(), entry.getValue()));
  }

  private static Wrapper<ProgramMethod> wrap(ProgramMethod method) {
    return ProgramMethodEquivalence.get().wrap(method);
  }
}
