// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner;

public class NopWhyAreYouNotKeepingReporter extends WhyAreYouNotInliningReporter {

  private static final NopWhyAreYouNotKeepingReporter INSTANCE =
      new NopWhyAreYouNotKeepingReporter();

  private NopWhyAreYouNotKeepingReporter() {}

  public static NopWhyAreYouNotKeepingReporter getInstance() {
    return INSTANCE;
  }

  @Override
  public void reportPotentialExplosionInExceptionalControlFlowResolutionBlocks(
      int estimatedNumberOfControlFlowResolutionBlocks, int threshold) {}

  @Override
  public void reportUnknownTarget() {}

  @Override
  public void reportWillExceedInstructionBudget(int numberOfInstructions, int threshold) {}

  @Override
  public boolean verifyReasonHasBeenReported() {
    return true;
  }
}
