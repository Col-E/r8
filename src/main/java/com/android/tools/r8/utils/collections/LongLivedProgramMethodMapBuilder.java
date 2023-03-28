// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntFunction;

public class LongLivedProgramMethodMapBuilder<V>
    extends LongLivedCollectionBuilder<Map<DexMethod, V>, ProgramMethodMap<?>> {

  private LongLivedProgramMethodMapBuilder(
      GraphLens currentGraphLens,
      IntFunction<ProgramMethodMap<?>> factory,
      IntFunction<Map<DexMethod, V>> factoryForBuilder) {
    super(currentGraphLens, factory, factoryForBuilder);
  }

  public static <V> LongLivedProgramMethodMapBuilder<V> create(GraphLens currentGraphLens) {
    return new LongLivedProgramMethodMapBuilder<>(
        currentGraphLens, ProgramMethodMap::create, IdentityHashMap::new);
  }

  public static <V> LongLivedProgramMethodMapBuilder<V> createConcurrentBuilderForNonConcurrentMap(
      GraphLens currentGraphLens) {
    return new LongLivedProgramMethodMapBuilder<>(
        currentGraphLens, ProgramMethodMap::create, ConcurrentHashMap::new);
  }

  public V computeIfAbsent(
      ProgramMethod key, Function<ProgramMethod, V> fn, GraphLens currentGraphLens) {
    assert verifyIsRewrittenWithLens(currentGraphLens);
    return backing.computeIfAbsent(key.getReference(), ignoreKey(() -> fn.apply(key)));
  }

  public boolean isEmpty() {
    return backing.isEmpty();
  }

  public void put(ProgramMethod key, V value, GraphLens currentGraphLens) {
    // All methods in a long lived program method set should be rewritten up until the same graph
    // lens.
    assert verifyIsRewrittenWithLens(currentGraphLens);
    backing.put(key.getReference(), value);
  }

  public LongLivedProgramMethodMapBuilder<V> rewrittenWithLens(
      AppView<AppInfoWithLiveness> appView, BiFunction<V, GraphLens, V> valueRewriter) {
    return rewrittenWithLens(valueRewriter, appView.graphLens());
  }

  public LongLivedProgramMethodMapBuilder<V> rewrittenWithLens(
      BiFunction<V, GraphLens, V> valueRewriter, GraphLens newGraphLens) {
    // Check if the graph lens has changed (otherwise lens rewriting is not needed).
    if (newGraphLens == appliedGraphLens) {
      return this;
    }

    // Rewrite the backing.
    Map<DexMethod, V> rewrittenBacking = factoryForBuilder.apply(backing.size());
    backing.forEach(
        (key, value) -> {
          DexMethod rewrittenKey = newGraphLens.getRenamedMethodSignature(key, appliedGraphLens);
          V rewrittenValue = valueRewriter.apply(value, appliedGraphLens);
          assert !rewrittenBacking.containsKey(rewrittenKey);
          rewrittenBacking.put(rewrittenKey, rewrittenValue);
        });
    backing = rewrittenBacking;

    // Record that this collection is now rewritten up until the given graph lens.
    appliedGraphLens = newGraphLens;
    return this;
  }

  @SuppressWarnings("unchecked")
  public <U> ProgramMethodMap<U> build(
      AppView<AppInfoWithLiveness> appView, Function<V, U> valueTransformer) {
    assert verifyIsRewrittenWithLens(appView.graphLens());

    ProgramMethodMap<U> result = (ProgramMethodMap<U>) factory.apply(backing.size());
    backing.forEach(
        (key, value) -> {
          DexProgramClass holder = asProgramClassOrNull(appView.definitionFor(key.getHolderType()));
          ProgramMethod method = key.lookupOnProgramClass(holder);
          if (method != null) {
            result.put(method, valueTransformer.apply(value));
          } else {
            assert false;
          }
        });
    return result;
  }
}
