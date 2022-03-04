// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.interfaces.analysis;

import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.IRCode;

public abstract class OpenClosedInterfacesAnalysis {

  public static EmptyOpenClosedInterfacesAnalysis empty() {
    return EmptyOpenClosedInterfacesAnalysis.getInstance();
  }

  public abstract void analyze(ProgramMethod method, IRCode code);

  public abstract void prepareForPrimaryOptimizationPass();

  public abstract void onPrimaryOptimizationPassComplete();
}
