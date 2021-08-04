// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation;

import com.android.tools.r8.errors.Unimplemented;

/**
 * Propagates the argument flow information collected by the {@link ArgumentPropagatorCodeScanner}.
 * This is needed to propagate argument information from call sites to all possible dispatch
 * targets.
 */
public class ArgumentPropagatorOptimizationInfoPopulator {

  /**
   * Computes an over-approximation of each parameter's value and type and stores the result in
   * {@link com.android.tools.r8.ir.optimize.info.MethodOptimizationInfo}.
   */
  void populateOptimizationInfo(ArgumentPropagatorCodeScannerResult codeScannerResult) {
    // TODO(b/190154391): Propagate argument information to handle virtual dispatch.
    // TODO(b/190154391): To deal with arguments that are themselves passed as arguments to invoke
    //  instructions, build a flow graph where nodes are parameters and there is an edge from a
    //  parameter p1 to p2 if the value of p2 is at least the value of p1. Then propagate the
    //  collected argument information throughout the flow graph.
    throw new Unimplemented();
  }
}
