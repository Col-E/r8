// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner;

import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.ir.code.InstancePut;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeDirect;
import java.io.PrintStream;

class WhyAreYouNotInliningReporterImpl extends WhyAreYouNotInliningReporter {

  private final DexEncodedMethod callee;
  private final DexEncodedMethod context;
  private final PrintStream output;

  private boolean reasonHasBeenReported = false;

  WhyAreYouNotInliningReporterImpl(
      DexEncodedMethod callee, DexEncodedMethod context, PrintStream output) {
    this.callee = callee;
    this.context = context;
    this.output = output;
  }

  private void print(String reason) {
    output.print("Method `");
    output.print(callee.method.toSourceString());
    output.print("` was not inlined into `");
    output.print(context.method.toSourceString());
    if (reason != null) {
      output.print("`: ");
      output.println(reason);
    } else {
      output.println("`.");
    }
    reasonHasBeenReported = true;
  }

  private void printWithExceededThreshold(
      String reason, String description, int value, int threshold) {
    print(reason + " (" + description + ": " + value + ", threshold: " + threshold + ").");
  }

  @Override
  public void reportInstructionBudgetIsExceeded() {
    print("caller's instruction budget is exceeded.");
  }

  @Override
  public void reportPotentialExplosionInExceptionalControlFlowResolutionBlocks(
      int estimatedNumberOfControlFlowResolutionBlocks, int threshold) {
    printWithExceededThreshold(
        "could lead to an explosion in the number of moves due to the exceptional control flow",
        "estimated number of control flow resolution blocks",
        estimatedNumberOfControlFlowResolutionBlocks,
        threshold);
  }

  // TODO(b/142108662): Always report a meaningful reason.
  @Override
  public void reportUnknownReason() {
    print(null);
  }

  @Override
  public void reportUnknownTarget() {
    print("could not find a single target.");
  }

  @Override
  public void reportUnsafeConstructorInliningDueToFinalFieldAssignment(InstancePut instancePut) {
    print(
        "final field `"
            + instancePut.getField()
            + "` must be initialized in a constructor of `"
            + callee.method.holder.toSourceString()
            + "`.");
  }

  @Override
  public void reportUnsafeConstructorInliningDueToIndirectConstructorCall(InvokeDirect invoke) {
    print(
        "must invoke a constructor from the class being instantiated (would invoke `"
            + invoke.getInvokedMethod().toSourceString()
            + "`).");
  }

  @Override
  public void reportUnsafeConstructorInliningDueToUninitializedObjectUse(Instruction user) {
    print("would lead to use of uninitialized object (user: `" + user.toString() + "`).");
  }

  @Override
  public void reportWillExceedInstructionBudget(int numberOfInstructions, int threshold) {
    printWithExceededThreshold(
        "would exceed the caller's instruction budget",
        "number of instructions in inlinee",
        numberOfInstructions,
        threshold);
  }

  @Override
  public boolean verifyReasonHasBeenReported() {
    assert reasonHasBeenReported;
    return true;
  }
}
