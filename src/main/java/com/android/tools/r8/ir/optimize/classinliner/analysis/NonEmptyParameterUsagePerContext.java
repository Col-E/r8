// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner.analysis;

import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

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

  public boolean allMatch(BiPredicate<AnalysisContext, ParameterUsage> predicate) {
    for (Map.Entry<AnalysisContext, ParameterUsage> entry : backing.entrySet()) {
      if (!predicate.test(entry.getKey(), entry.getValue())) {
        return false;
      }
    }
    return true;
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
  ParameterUsagePerContext externalize() {
    boolean allBottom = true;
    boolean allTop = true;
    for (ParameterUsage usage : backing.values()) {
      if (!usage.isBottom()) {
        allBottom = false;
      }
      if (!usage.isTop()) {
        allTop = false;
      }
    }
    if (allBottom) {
      return bottom();
    }
    if (allTop) {
      return top();
    }
    // Remove mappings to top. These mappings represent unknown information, which there is no point
    // in storing. After the removal of these mappings, the result should still be non-empty.
    ParameterUsagePerContext rebuilt =
        rebuild((context, usage) -> usage.isTop() ? null : usage.externalize());
    assert !rebuilt.isBottom();
    assert !rebuilt.isTop();
    return rebuilt;
  }

  @Override
  public ParameterUsage get(AnalysisContext context) {
    return backing.getOrDefault(context, ParameterUsage.top());
  }

  public int getNumberOfContexts() {
    return backing.size();
  }

  @Override
  ParameterUsagePerContext rebuild(
      BiFunction<AnalysisContext, ParameterUsage, ParameterUsage> transformation) {
    ImmutableMap.Builder<AnalysisContext, ParameterUsage> builder = null;
    for (Map.Entry<AnalysisContext, ParameterUsage> entry : backing.entrySet()) {
      AnalysisContext context = entry.getKey();
      ParameterUsage usage = entry.getValue();
      ParameterUsage newUsage = transformation.apply(context, usage);
      if (newUsage != null) {
        if (newUsage != usage) {
          if (builder == null) {
            builder = ImmutableMap.builder();
            for (Map.Entry<AnalysisContext, ParameterUsage> previousEntry : backing.entrySet()) {
              AnalysisContext previousContext = previousEntry.getKey();
              if (previousContext == context) {
                break;
              }
              builder.put(previousContext, previousEntry.getValue());
            }
          }
          builder.put(context, newUsage);
        } else if (builder != null) {
          builder.put(context, newUsage);
        }
      }
    }
    return builder != null ? create(builder.build()) : this;
  }

  @Override
  @SuppressWarnings("EqualsGetClass")
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
