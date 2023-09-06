// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner.analysis;

import com.android.tools.r8.ir.code.AssumeAndCheckCastAliasedValueConfiguration;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.utils.ArrayUtils;
import com.android.tools.r8.utils.Int2ObjectMapUtils;
import com.android.tools.r8.utils.IntObjConsumer;
import com.android.tools.r8.utils.IntObjPredicate;
import com.android.tools.r8.utils.IntObjToObjFunction;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * This implements the lattice for the dataflow analysis used to determine if a given method is
 * eligible for class inlining.
 *
 * <p>Given a value that is subject to class inlining (e.g., `A a = new A()`), we need to determine
 * if the value `a` will be eligible for class inlining if it flows into a method call. The value
 * `a` may flow into any argument position, therefore we need to be able to determine if calls where
 * `a` is used as a receiver are eligible for class inlining {@code a.foo(...)}, as well as calls
 * where `a` is not used as a receiver: {@code other.foo(a)} or {@code Clazz.foo(a)}.
 *
 * <p>To answer such questions, this lattice contains information about the way a method uses its
 * parameters. For a given parameter, this information is encoded in {@link ParameterUsage}.
 *
 * <p>To facilitate context sensitive information, {@link ParameterUsagePerContext} gives the
 * parameter usage information for a given parameter in a given context. As a simple example,
 * consider the following method:
 *
 * <pre>
 *   int x;
 *   void foo() {
 *     if (this.x == 0) {
 *       // Do nothing.
 *     } else {
 *       System.out.println(x);
 *     }
 *   }
 * </pre>
 *
 * <p>In the above example, the parameter `this` is not eligible for class inlining if `this.x !=
 * 0`. However, when `this.x == 0`, the parameter usage information is bottom. This piece of
 * information is encoded as a map lattice from contexts to parameter usage information:
 *
 * <pre>
 *   ParameterUsagePerContext[
 *       Context[this.x == 0]  ->  BottomParameterUsage  (BOTTOM),
 *       DefaultContext        ->  UnknownParameterUsage (TOP)
 *   ]
 * </pre>
 *
 * <p>Finally, to provide the information for each method parameter, this class provides a mapping
 * from parameters to {@link ParameterUsagePerContext}.
 */
public class NonEmptyParameterUsages extends ParameterUsages {

  private static final AssumeAndCheckCastAliasedValueConfiguration aliasedValueConfiguration =
      AssumeAndCheckCastAliasedValueConfiguration.getInstance();

  private final Int2ObjectMap<ParameterUsagePerContext> backing;

  private NonEmptyParameterUsages(Int2ObjectMap<ParameterUsagePerContext> backing) {
    assert !backing.isEmpty() : "Should use bottom() instead";
    this.backing = backing;
  }

  public static ParameterUsages create(Int2ObjectMap<ParameterUsagePerContext> backing) {
    return backing.isEmpty() ? bottom() : new NonEmptyParameterUsages(backing);
  }

  public boolean allMatch(IntObjPredicate<ParameterUsagePerContext> predicate) {
    for (Int2ObjectMap.Entry<ParameterUsagePerContext> entry : backing.int2ObjectEntrySet()) {
      if (!predicate.test(entry.getIntKey(), entry.getValue())) {
        return false;
      }
    }
    return true;
  }

  @Override
  public NonEmptyParameterUsages asNonEmpty() {
    return this;
  }

  @Override
  ParameterUsages externalize() {
    NonEmptyParameterUsages rebuilt =
        rebuildParameters((parameter, usagePerContext) -> usagePerContext.externalize());
    boolean allBottom = true;
    boolean allTop = true;
    for (ParameterUsagePerContext usagePerContext : rebuilt.backing.values()) {
      if (!usagePerContext.isBottom()) {
        allBottom = false;
      }
      if (!usagePerContext.isTop()) {
        allTop = false;
      }
    }
    if (allBottom) {
      return bottom();
    }
    if (allTop) {
      return top();
    }
    return rebuilt;
  }

  @Override
  ParameterUsages put(int parameter, ParameterUsagePerContext parameterUsagePerContext) {
    Int2ObjectOpenHashMap<ParameterUsagePerContext> newBacking =
        new Int2ObjectOpenHashMap<>(backing);
    newBacking.put(parameter, parameterUsagePerContext);
    return create(newBacking);
  }

  public void forEach(IntObjConsumer<ParameterUsagePerContext> consumer) {
    Int2ObjectMapUtils.forEach(backing, consumer);
  }

  @Override
  public ParameterUsagePerContext get(int parameter) {
    return backing.getOrDefault(parameter, ParameterUsagePerContext.top());
  }

  NonEmptyParameterUsages abandonClassInliningInCurrentContexts(Value value) {
    return rebuildParameter(value, (context, usage) -> ParameterUsage.top());
  }

  NonEmptyParameterUsages abandonClassInliningInCurrentContexts(Collection<Value> values) {
    if (values.isEmpty()) {
      return this;
    }
    int[] parametersToRebuild = new int[values.size()];
    Iterator<Value> iterator = values.iterator();
    for (int i = 0; i < values.size(); i++) {
      parametersToRebuild[i] = iterator.next().getDefinition().asArgument().getIndex();
    }
    return rebuildParameters(
        (currentParameter, usagePerContext) ->
            ArrayUtils.containsInt(parametersToRebuild, currentParameter)
                ? usagePerContext.rebuild((context, usage) -> ParameterUsage.top())
                : usagePerContext);
  }

  NonEmptyParameterUsages abandonClassInliningInCurrentContexts(
      Iterable<Value> values, Predicate<Value> predicate) {
    List<Value> filtered = new ArrayList<>();
    for (Value value : values) {
      Value root = value.getAliasedValue(aliasedValueConfiguration);
      if (predicate.test(root)) {
        filtered.add(root);
      }
    }
    return abandonClassInliningInCurrentContexts(filtered);
  }

  NonEmptyParameterUsages rebuildParameter(
      Value value, BiFunction<AnalysisContext, ParameterUsage, ParameterUsage> transformation) {
    Value valueRoot = value.getAliasedValue(aliasedValueConfiguration);
    assert valueRoot.isArgument();
    int parameter = valueRoot.getDefinition().asArgument().getIndex();
    return rebuildParameters(
        (currentParameter, usagePerContext) ->
            currentParameter == parameter
                ? usagePerContext.rebuild(transformation)
                : usagePerContext);
  }

  @Override
  NonEmptyParameterUsages rebuildParameters(
      IntObjToObjFunction<ParameterUsagePerContext, ParameterUsagePerContext> transformation) {
    Int2ObjectMap<ParameterUsagePerContext> rebuiltBacking = null;
    for (Int2ObjectMap.Entry<ParameterUsagePerContext> entry : backing.int2ObjectEntrySet()) {
      int parameter = entry.getIntKey();
      ParameterUsagePerContext usagePerContext = entry.getValue();
      ParameterUsagePerContext newUsagePerContext =
          transformation.apply(parameter, entry.getValue());
      if (newUsagePerContext != usagePerContext) {
        if (rebuiltBacking == null) {
          rebuiltBacking = new Int2ObjectOpenHashMap<>();
          for (Int2ObjectMap.Entry<ParameterUsagePerContext> previousEntry :
              backing.int2ObjectEntrySet()) {
            int previousParameter = previousEntry.getIntKey();
            if (previousParameter == parameter) {
              break;
            }
            rebuiltBacking.put(previousParameter, previousEntry.getValue());
          }
        }
        rebuiltBacking.put(parameter, newUsagePerContext);
      } else if (rebuiltBacking != null) {
        rebuiltBacking.put(parameter, newUsagePerContext);
      }
    }
    return rebuiltBacking != null ? new NonEmptyParameterUsages(rebuiltBacking) : this;
  }

  public NonEmptyParameterUsages join(NonEmptyParameterUsages otherAnalysisState) {
    if (isBottom()) {
      return otherAnalysisState;
    }
    if (otherAnalysisState.isBottom()) {
      return this;
    }
    Int2ObjectMap<ParameterUsagePerContext> newBacking = new Int2ObjectOpenHashMap<>(backing);
    otherAnalysisState.forEach(
        (parameter, parameterUsagePerContext) ->
            newBacking.put(
                parameter,
                parameterUsagePerContext.join(
                    Int2ObjectMapUtils.getOrDefault(
                        newBacking, parameter, ParameterUsagePerContext.bottom()))));
    return new NonEmptyParameterUsages(newBacking);
  }

  @Override
  @SuppressWarnings("EqualsGetClass")
  public boolean equals(Object other) {
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    NonEmptyParameterUsages analysisState = (NonEmptyParameterUsages) other;
    return backing.equals(analysisState.backing);
  }

  @Override
  public int hashCode() {
    return backing.hashCode();
  }
}
