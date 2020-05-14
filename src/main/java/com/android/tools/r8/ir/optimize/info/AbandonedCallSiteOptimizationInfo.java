// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.info;

/**
 * Used to represent that nothing is known about the argument values of a given method and all of
 * its overrides.
 */
public class AbandonedCallSiteOptimizationInfo extends CallSiteOptimizationInfo {

  private static final AbandonedCallSiteOptimizationInfo INSTANCE =
      new AbandonedCallSiteOptimizationInfo();

  private AbandonedCallSiteOptimizationInfo() {}

  static AbandonedCallSiteOptimizationInfo getInstance() {
    return INSTANCE;
  }

  @Override
  public boolean isAbandoned() {
    return true;
  }
}
