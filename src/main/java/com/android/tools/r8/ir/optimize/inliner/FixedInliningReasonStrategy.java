// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner;

import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.optimize.DefaultInliningOracle;
import com.android.tools.r8.ir.optimize.Inliner.Reason;

public class FixedInliningReasonStrategy implements InliningReasonStrategy {

  private final Reason reason;

  public FixedInliningReasonStrategy(Reason reason) {
    this.reason = reason;
  }

  @Override
  public Reason computeInliningReason(
      InvokeMethod invoke,
      ProgramMethod target,
      ProgramMethod context,
      DefaultInliningOracle oracle,
      InliningIRProvider inliningIRProvider,
      MethodProcessor methodProcessor) {
    return reason;
  }
}
