// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.graph.ProgramMethod;

public class EmptyCfInstructionDesugaringCollection extends CfInstructionDesugaringCollection {

  @Override
  public void desugar(ProgramMethod method, CfInstructionDesugaringEventConsumer consumer) {
    // Intentionally empty.
  }

  @Override
  public boolean needsDesugaring(ProgramMethod method) {
    return false;
  }
}
