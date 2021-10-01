// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner.analysis;

import com.android.tools.r8.utils.IntObjToObjFunction;

public class UnknownParameterUsages extends ParameterUsages {

  private static final UnknownParameterUsages INSTANCE = new UnknownParameterUsages();

  private UnknownParameterUsages() {}

  static UnknownParameterUsages getInstance() {
    return INSTANCE;
  }

  @Override
  ParameterUsages externalize() {
    return this;
  }

  @Override
  public ParameterUsagePerContext get(int parameter) {
    return ParameterUsagePerContext.top();
  }

  @Override
  public boolean isTop() {
    return true;
  }

  @Override
  ParameterUsages put(int parameter, ParameterUsagePerContext usagePerContext) {
    return this;
  }

  @Override
  ParameterUsages rebuildParameters(
      IntObjToObjFunction<ParameterUsagePerContext, ParameterUsagePerContext> transformation) {
    return this;
  }

  @Override
  public boolean equals(Object other) {
    return other == INSTANCE;
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(this);
  }
}
