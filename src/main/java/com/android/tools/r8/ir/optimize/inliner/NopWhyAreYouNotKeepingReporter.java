// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner;

import com.android.tools.r8.ir.code.InstancePut;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeDirect;

public class NopWhyAreYouNotKeepingReporter extends WhyAreYouNotInliningReporter {

  private static final NopWhyAreYouNotKeepingReporter INSTANCE =
      new NopWhyAreYouNotKeepingReporter();

  private NopWhyAreYouNotKeepingReporter() {}

  public static NopWhyAreYouNotKeepingReporter getInstance() {
    return INSTANCE;
  }

  @Override
  public void reportInstructionBudgetIsExceeded() {}

  @Override
  public void reportPotentialExplosionInExceptionalControlFlowResolutionBlocks(
      int estimatedNumberOfControlFlowResolutionBlocks, int threshold) {}

  @Override
  public void reportUnknownTarget() {}

  @Override
  public void reportUnsafeConstructorInliningDueToFinalFieldAssignment(InstancePut instancePut) {}

  @Override
  public void reportUnsafeConstructorInliningDueToIndirectConstructorCall(InvokeDirect invoke) {}

  @Override
  public void reportUnsafeConstructorInliningDueToUninitializedObjectUse(Instruction user) {}

  @Override
  public void reportWillExceedInstructionBudget(int numberOfInstructions, int threshold) {}

  @Override
  public boolean verifyReasonHasBeenReported() {
    return true;
  }
}
