// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner;

import com.android.tools.r8.androidapi.ComputedApiLevel;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.InstancePut;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.optimize.Inliner.Reason;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringUtils;
import java.util.Set;

class WhyAreYouNotInliningReporterImpl extends WhyAreYouNotInliningReporter {

  private final ProgramMethod callee;
  private final ProgramMethod context;
  private final Reporter reporter;

  private boolean reasonHasBeenReported = false;

  WhyAreYouNotInliningReporterImpl(
      AppView<?> appView, ProgramMethod callee, ProgramMethod context) {
    this.callee = callee;
    this.context = context;
    reporter = appView.reporter();
  }

  private void report(String reason) {
    StringBuilder message = new StringBuilder();
    message.append("Method `");
    message.append(callee.toSourceString());
    message.append("` was not inlined into `");
    message.append(context.toSourceString());
    if (reason != null) {
      message.append("`: ");
      message.append(reason);
    } else {
      message.append("`.");
    }
    reporter.info(new WhyAreYouNotInliningDiagnostic(context.getOrigin(), message.toString()));
    reasonHasBeenReported = true;
  }

  private void printWithExceededThreshold(
      String reason, String description, int value, int threshold) {
    report(reason + " (" + description + ": " + value + ", threshold: " + threshold + ").");
  }

  @Override
  public void reportCallerNotSameClass() {
    report("inlinee can only be inlined into methods in the same class.");
  }

  @Override
  public void reportCallerNotSameNest() {
    report("inlinee can only be inlined into methods in the same class (and its nest members).");
  }

  @Override
  public void reportCallerNotSamePackage() {
    report(
        "inlinee can only be inlined into methods in the same package "
            + "(declared package private or accesses package private type or member).");
  }

  @Override
  public void reportCallerNotSubtype() {
    report(
        "inlinee can only be inlined into methods in the same package and methods in subtypes of "
            + "the inlinee's enclosing class"
            + "(declared protected or accesses protected type or member).");
  }

  @Override
  public void reportCallerHasUnknownApiLevel() {
    report("computed API level for caller is unknown");
  }

  @Override
  public void reportClasspathMethod() {
    report("inlinee is on the classpath.");
  }

  @Override
  public void reportInaccessible() {
    report("inlinee is not accessible from the caller context.");
  }

  @Override
  public void reportIncorrectArity(int numberOfArguments, int arity) {
    report(
        "number of arguments ("
            + numberOfArguments
            + ") does not match arity of method ("
            + arity
            + ").");
  }

  @Override
  public void reportInlineeDoesNotHaveCode() {
    report("inlinee does not have code.");
  }

  @Override
  public void reportInlineeNotInliningCandidate() {
    report("unsupported instruction in inlinee.");
  }

  @Override
  public void reportInlineeNotProcessed() {
    report("inlinee not processed yet.");
  }

  @Override
  public void reportInlineeNotSimple() {
    report(
        "not inlining due to code size heuristic "
            + "(inlinee may have multiple callers and is not considered trivial).");
  }

  @Override
  public void reportInlineeHigherApiCall(
      ComputedApiLevel callerApiLevel, ComputedApiLevel inlineeApiLevel) {
    assert callerApiLevel.isKnownApiLevel();
    if (inlineeApiLevel.isUnknownApiLevel()) {
      report("computed API level for inlinee is unknown");
    } else {
      assert inlineeApiLevel.isKnownApiLevel();
      report(
          "computed API level for inlinee ("
              + inlineeApiLevel.asKnownApiLevel().getApiLevel()
              + ") is higher than caller's ("
              + callerApiLevel.asKnownApiLevel().getApiLevel()
              + ")");
    }
  }

  @Override
  public void reportInlineeRefersToClassesNotInMainDex() {
    report(
        "inlining could increase the main dex size "
            + "(caller is in main dex and inlinee refers to classes not in main dex).");
  }

  @Override
  public void reportInliningAcrossFeatureSplit() {
    report("cannot inline across feature splits.");
  }

  @Override
  public void reportInstructionBudgetIsExceeded() {
    report("caller's instruction budget is exceeded.");
  }

  @Override
  public void reportInvalidDoubleInliningCandidate() {
    report("inlinee is invoked more than once and could not be inlined into all call sites.");
  }

  @Override
  public void reportInvalidInliningReason(Reason reason, Set<Reason> validInliningReasons) {
    report(
        "not a valid inlining reason (was: "
            + reason
            + ", allowed: one of "
            + StringUtils.join(", ", validInliningReasons)
            + ").");
  }

  @Override
  public void reportLibraryMethod() {
    report("inlinee is a library method.");
  }

  @Override
  public void reportMarkedAsNeverInline() {
    report("method is marked by a -neverinline rule.");
  }

  @Override
  public void reportMustTriggerClassInitialization() {
    report(
        "cannot guarantee that the enclosing class of the inlinee is guaranteed to be class "
            + "initialized before the first side-effecting instruction in the inlinee.");
  }

  @Override
  public void reportNoInliningIntoConstructorsWhenGeneratingClassFiles() {
    report("inlining into constructors not supported when generating class files.");
  }

  @Override
  public void reportPinned() {
    report("method is kept by a Proguard configuration rule.");
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

  @Override
  public void reportProcessedConcurrently() {
    report(
        "could lead to nondeterministic output since the inlinee is being optimized concurrently.");
  }

  @Override
  public void reportReceiverDefinitelyNull() {
    report("the receiver is always null at the call site.");
  }

  @Override
  public void reportReceiverMaybeNull() {
    report("the receiver may be null at the call site.");
  }

  @Override
  public void reportRecursiveMethod() {
    report("recursive calls are not inlined.");
  }

  @Override
  public void reportUnknownTarget() {
    report("could not find a single target.");
  }

  @Override
  public void reportUnsafeConstructorInliningDueToFinalFieldAssignment(InstancePut instancePut) {
    report(
        "final field `"
            + instancePut.getField()
            + "` must be initialized in a constructor of `"
            + callee.getHolderType().toSourceString()
            + "`.");
  }

  @Override
  public void reportUnsafeConstructorInliningDueToIndirectConstructorCall(InvokeDirect invoke) {
    report(
        "must invoke a constructor from the class being instantiated (would invoke `"
            + invoke.getInvokedMethod().toSourceString()
            + "`).");
  }

  @Override
  public void reportUnsafeConstructorInliningDueToUninitializedObjectUse(Instruction user) {
    report("would lead to use of uninitialized object (user: `" + user.toString() + "`).");
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
  public void reportWillExceedMaxInliningDepth(int actualInliningDepth, int threshold) {
    printWithExceededThreshold(
        "would exceed the maximum inlining depth",
        "current inlining depth",
        actualInliningDepth,
        threshold);
  }

  @Override
  public void reportWillExceedMonitorEnterValuesBudget(
      int numberOfMonitorEnterValuesAfterInlining, int threshold) {
    printWithExceededThreshold(
        "could negatively impact register allocation due to the number of monitor instructions",
        "estimated number of locks after inlining",
        numberOfMonitorEnterValuesAfterInlining,
        threshold);
  }

  @Override
  public boolean unsetReasonHasBeenReportedFlag() {
    assert reasonHasBeenReported;
    reasonHasBeenReported = false;
    return true;
  }
}
