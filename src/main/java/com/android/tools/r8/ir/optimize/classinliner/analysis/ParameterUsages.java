// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner.analysis;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.AbstractState;
import com.android.tools.r8.utils.IntObjToObjFunction;

public abstract class ParameterUsages extends AbstractState<ParameterUsages> {

  @Override
  public ParameterUsages asAbstractState() {
    return this;
  }

  public NonEmptyParameterUsages asNonEmpty() {
    return null;
  }

  /**
   * Prepares this instance for being stored in the optimization info. This converts instances
   * inside this {@link ParameterUsages} instance that are not suitable for being stored in
   * optimization info into instances that can be stored in the optimization info.
   *
   * <p>For example, converts instances of {@link InternalNonEmptyParameterUsage} to {@link
   * NonEmptyParameterUsage}. This is needed because {@link InternalNonEmptyParameterUsage} is not
   * suitable for being stored in {@link
   * com.android.tools.r8.ir.optimize.info.MethodOptimizationInfo}, since it contains references to
   * IR instructions.
   */
  abstract ParameterUsages externalize();

  public abstract ParameterUsagePerContext get(int parameter);

  public boolean isBottom() {
    return false;
  }

  public boolean isTop() {
    return false;
  }

  @Override
  public ParameterUsages join(AppView<?> appView, ParameterUsages state) {
    if (isBottom()) {
      return state;
    }
    if (state.isBottom()) {
      return this;
    }
    if (isTop() || state.isTop()) {
      return top();
    }
    return asNonEmpty().join(state.asNonEmpty());
  }

  abstract ParameterUsages put(int parameter, ParameterUsagePerContext usagePerContext);

  abstract ParameterUsages rebuildParameters(
      IntObjToObjFunction<ParameterUsagePerContext, ParameterUsagePerContext> transformation);

  public static BottomParameterUsages bottom() {
    return BottomParameterUsages.getInstance();
  }

  public static UnknownParameterUsages top() {
    return UnknownParameterUsages.getInstance();
  }
}
