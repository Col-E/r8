// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation;

import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.Map;
import java.util.Set;

/**
 * Analyzes each {@link IRCode} during the primary optimization to collect information about the
 * arguments passed to method parameters.
 *
 * <p>State pruning is applied on-the-fly to avoid storing redundant information.
 */
class ArgumentPropagatorCodeScanner {

  private final AppView<AppInfoWithLiveness> appView;

  /**
   * Maps each non-interface method to the upper most method in the super class chain with the same
   * method signature. This only contains an entry for non-private virtual methods that override
   * another method in the program.
   */
  private final Map<DexMethod, DexMethod> classMethodRoots;

  /**
   * The methods that are not subject to argument propagation. This includes (i) methods that are
   * not subject to optimization due to -keep rules, (ii) classpath/library method overrides, and
   * (iii) methods that are unlikely to benefit from argument propagation according to heuristics.
   *
   * <p>Argument propagation must also be disabled for lambda implementation methods unless we model
   * the calls from lambda main methods synthesized by the JVM.
   */
  private final Set<DexMethod> unoptimizableMethods;

  ArgumentPropagatorCodeScanner(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
    this.classMethodRoots = computeClassMethodRoots();
    this.unoptimizableMethods = computeUnoptimizableMethods();
  }

  private Map<DexMethod, DexMethod> computeClassMethodRoots() {
    throw new Unimplemented();
  }

  private Set<DexMethod> computeUnoptimizableMethods() {
    throw new Unimplemented();
  }

  ArgumentPropagatorCodeScannerResult getResult() {
    throw new Unimplemented();
  }

  void scan(ProgramMethod method, IRCode code) {
    throw new Unimplemented();
  }
}
