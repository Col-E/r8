// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import com.android.tools.r8.graph.lens.GraphLens;
import java.util.function.IntFunction;

public abstract class LongLivedCollectionBuilder<BuilderCollection, ResultCollection> {

  // Factory for creating the final result collection.
  protected final IntFunction<ResultCollection> factory;

  // Factory for creating a builder collection.
  protected final IntFunction<BuilderCollection> factoryForBuilder;

  // The graph lens that this collection has been rewritten up until.
  protected GraphLens appliedGraphLens;

  // The underlying backing.
  protected BuilderCollection backing;

  protected LongLivedCollectionBuilder(
      GraphLens currentGraphLens,
      IntFunction<ResultCollection> factory,
      IntFunction<BuilderCollection> factoryForBuilder) {
    this.appliedGraphLens = currentGraphLens;
    this.factory = factory;
    this.factoryForBuilder = factoryForBuilder;
    this.backing = factoryForBuilder.apply(2);
  }

  public boolean isRewrittenWithLens(GraphLens graphLens) {
    return appliedGraphLens == graphLens;
  }

  public boolean verifyIsRewrittenWithLens(GraphLens graphLens) {
    assert isRewrittenWithLens(graphLens);
    return true;
  }
}
