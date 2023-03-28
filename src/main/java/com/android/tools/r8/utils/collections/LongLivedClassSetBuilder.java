// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.SetUtils;
import java.util.Set;
import java.util.function.IntFunction;

public class LongLivedClassSetBuilder<T extends DexClass>
    extends LongLivedCollectionBuilder<Set<DexType>, Set<T>> {

  private LongLivedClassSetBuilder(
      GraphLens currentGraphLens,
      IntFunction<Set<T>> factory,
      IntFunction<Set<DexType>> factoryForBuilder) {
    super(currentGraphLens, factory, factoryForBuilder);
  }

  public static <T extends DexClass>
      LongLivedClassSetBuilder<T> createConcurrentBuilderForIdentitySet(
          GraphLens currentGraphLens) {
    return new LongLivedClassSetBuilder<>(
        currentGraphLens, SetUtils::newIdentityHashSet, SetUtils::newConcurrentHashSet);
  }

  public void add(T clazz, GraphLens currentGraphLens) {
    // All classes in a long lived class set should be rewritten up until the same graph lens.
    assert verifyIsRewrittenWithLens(currentGraphLens);
    backing.add(clazz.getType());
  }

  public LongLivedClassSetBuilder<T> rewrittenWithLens(AppView<AppInfoWithLiveness> appView) {
    return rewrittenWithLens(appView.graphLens());
  }

  public LongLivedClassSetBuilder<T> rewrittenWithLens(GraphLens newGraphLens) {
    // Check if the graph lens has changed (otherwise lens rewriting is not needed).
    if (newGraphLens == appliedGraphLens) {
      return this;
    }

    // Rewrite the backing.
    Set<DexType> rewrittenBacking = factoryForBuilder.apply(backing.size());
    for (DexType type : backing) {
      rewrittenBacking.add(newGraphLens.lookupType(type, appliedGraphLens));
    }
    backing = rewrittenBacking;

    // Record that this collection is now rewritten up until the given graph lens.
    appliedGraphLens = newGraphLens;
    return this;
  }

  @SuppressWarnings("unchecked")
  public Set<T> build(AppView<AppInfoWithLiveness> appView) {
    Set<T> result = factory.apply(backing.size());
    for (DexType type : backing) {
      DexType rewrittenType = appView.graphLens().lookupType(type, appliedGraphLens);
      T clazz = (T) appView.definitionFor(rewrittenType);
      if (clazz != null) {
        result.add(clazz);
      } else {
        assert false : "Unable to find definition for: " + rewrittenType.getTypeName();
      }
    }
    return result;
  }
}
