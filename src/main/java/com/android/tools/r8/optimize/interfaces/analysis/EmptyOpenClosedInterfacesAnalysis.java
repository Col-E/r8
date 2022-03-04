// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.interfaces.analysis;

import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.IRCode;

public class EmptyOpenClosedInterfacesAnalysis extends OpenClosedInterfacesAnalysis {

  private static final EmptyOpenClosedInterfacesAnalysis INSTANCE =
      new EmptyOpenClosedInterfacesAnalysis();

  private EmptyOpenClosedInterfacesAnalysis() {}

  static EmptyOpenClosedInterfacesAnalysis getInstance() {
    return INSTANCE;
  }

  @Override
  public void analyze(ProgramMethod method, IRCode code) {
    // Intentionally empty.
  }

  @Override
  public void prepareForPrimaryOptimizationPass() {
    // Intentionally empty.
  }

  @Override
  public void onPrimaryOptimizationPassComplete() {
    // Intentionally empty.
  }
}
