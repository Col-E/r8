// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner.analysis;

import java.util.function.BiFunction;

public abstract class ParameterUsagePerContext {

  NonEmptyParameterUsagePerContext asKnown() {
    return null;
  }

  /** Returns the usage information for this parameter in the given context. */
  public abstract ParameterUsage get(AnalysisContext context);

  /**
   * Returns true if this is an instance of {@link BottomParameterUsagePerContext}.
   *
   * <p>In this case, the given parameter is always eligible for class inlining.
   */
  public boolean isBottom() {
    return false;
  }

  /**
   * Returns true if this is an instance of {@link UnknownParameterUsagePerContext}.
   *
   * <p>In this case, the given parameter is never eligible for class inlining.
   */
  public boolean isTop() {
    return false;
  }

  ParameterUsagePerContext join(ParameterUsagePerContext parameterUsagePerContext) {
    if (isTop() || parameterUsagePerContext.isTop()) {
      return top();
    }
    return asKnown().join(parameterUsagePerContext.asKnown());
  }

  abstract ParameterUsagePerContext rebuild(
      BiFunction<AnalysisContext, ParameterUsage, ParameterUsage> transformation);

  static BottomParameterUsagePerContext bottom() {
    return BottomParameterUsagePerContext.getInstance();
  }

  static UnknownParameterUsagePerContext top() {
    return UnknownParameterUsagePerContext.getInstance();
  }
}
