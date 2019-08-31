// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.info;

import com.android.tools.r8.ir.analysis.type.Nullability;

public class DefaultCallSiteOptimizationInfo extends CallSiteOptimizationInfo {

  private static final DefaultCallSiteOptimizationInfo INSTANCE =
      new DefaultCallSiteOptimizationInfo();

  private DefaultCallSiteOptimizationInfo() {}

  public static DefaultCallSiteOptimizationInfo getInstance() {
    return INSTANCE;
  }

  @Override
  public Nullability getNullability(int argIndex) {
    return Nullability.maybeNull();
  }

  @Override
  public boolean isDefaultCallSiteOptimizationInfo() {
    return true;
  }

  @Override
  public DefaultCallSiteOptimizationInfo asDefaultCallSiteOptimizationInfo() {
    return this;
  }
}
