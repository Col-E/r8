// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner.analysis;

import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

class NonEmptyParameterUsagePerContext extends ParameterUsagePerContext {

  private final Map<AnalysisContext, ParameterUsage> backing;

  private NonEmptyParameterUsagePerContext(Map<AnalysisContext, ParameterUsage> backing) {
    assert !backing.isEmpty();
    this.backing = backing;
  }

  static ParameterUsagePerContext create(Map<AnalysisContext, ParameterUsage> backing) {
    return backing.isEmpty() ? bottom() : new NonEmptyParameterUsagePerContext(backing);
  }

  static NonEmptyParameterUsagePerContext createInitial() {
    return new NonEmptyParameterUsagePerContext(
        ImmutableMap.of(DefaultAnalysisContext.getInstance(), ParameterUsage.bottom()));
  }

  void forEach(BiConsumer<AnalysisContext, ParameterUsage> consumer) {
    backing.forEach(consumer);
  }

  ParameterUsagePerContext join(NonEmptyParameterUsagePerContext parameterUsagePerContext) {
    if (isBottom()) {
      return parameterUsagePerContext;
    }
    if (parameterUsagePerContext.isBottom()) {
      return this;
    }
    Map<AnalysisContext, ParameterUsage> newBacking = new HashMap<>(backing);
    parameterUsagePerContext.forEach(
        (context, parameterUsage) ->
            newBacking.put(
                context,
                parameterUsage.join(newBacking.getOrDefault(context, ParameterUsage.bottom()))));
    return create(newBacking);
  }

  @Override
  NonEmptyParameterUsagePerContext asKnown() {
    return this;
  }

  @Override
  public ParameterUsage get(AnalysisContext context) {
    assert backing.containsKey(context);
    return backing.get(context);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null || obj.getClass() != getClass()) {
      return false;
    }
    NonEmptyParameterUsagePerContext knownParameterUsagePerContext =
        (NonEmptyParameterUsagePerContext) obj;
    return backing.equals(knownParameterUsagePerContext.backing);
  }

  @Override
  public int hashCode() {
    return backing.hashCode();
  }
}
