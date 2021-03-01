// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner.analysis;

import com.android.tools.r8.ir.analysis.framework.intraprocedural.AbstractState;
import com.android.tools.r8.utils.Int2ObjectMapUtils;
import com.android.tools.r8.utils.IntObjConsumer;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

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
public class AnalysisState extends AbstractState<AnalysisState> {

  private static final AnalysisState BOTTOM = new AnalysisState();

  private final Int2ObjectMap<ParameterUsagePerContext> backing;

  private AnalysisState() {
    this.backing = Int2ObjectMaps.emptyMap();
  }

  private AnalysisState(Int2ObjectMap<ParameterUsagePerContext> backing) {
    assert !backing.isEmpty() : "Should use bottom() instead";
    this.backing = backing;
  }

  static AnalysisState bottom() {
    return BOTTOM;
  }

  static AnalysisState create(Int2ObjectMap<ParameterUsagePerContext> backing) {
    return backing.isEmpty() ? bottom() : new AnalysisState(backing);
  }

  AnalysisState put(int parameter, ParameterUsagePerContext parameterUsagePerContext) {
    Int2ObjectOpenHashMap<ParameterUsagePerContext> newBacking =
        new Int2ObjectOpenHashMap<>(backing);
    newBacking.put(parameter, parameterUsagePerContext);
    return create(newBacking);
  }

  void forEach(IntObjConsumer<ParameterUsagePerContext> consumer) {
    Int2ObjectMapUtils.forEach(backing, consumer);
  }

  public ParameterUsagePerContext get(int parameter) {
    assert backing.containsKey(parameter);
    ParameterUsagePerContext value = backing.get(parameter);
    return value != null ? value : ParameterUsagePerContext.bottom();
  }

  public boolean isBottom() {
    assert !backing.isEmpty() || this == bottom();
    return backing.isEmpty();
  }

  @Override
  public AnalysisState asAbstractState() {
    return this;
  }

  @Override
  public AnalysisState join(AnalysisState otherAnalysisState) {
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
    return AnalysisState.create(newBacking);
  }

  @Override
  public boolean equals(Object other) {
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    AnalysisState analysisState = (AnalysisState) other;
    return backing.equals(analysisState.backing);
  }

  @Override
  public int hashCode() {
    return backing.hashCode();
  }
}
