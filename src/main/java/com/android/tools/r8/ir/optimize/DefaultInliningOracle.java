// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.TypeAnalysis;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;
import com.android.tools.r8.ir.code.InvokePolymorphic;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.CallSiteInformation;
import com.android.tools.r8.ir.optimize.Inliner.InlineAction;
import com.android.tools.r8.ir.optimize.Inliner.Reason;
import com.android.tools.r8.logging.Log;
import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.IteratorUtils;
import java.util.BitSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.function.Predicate;

final class DefaultInliningOracle implements InliningOracle, InliningStrategy {

  private final AppView<? extends AppInfoWithLiveness> appView;
  private final Inliner inliner;
  private final DexEncodedMethod method;
  private final IRCode code;
  private final CallSiteInformation callSiteInformation;
  private final Predicate<DexEncodedMethod> isProcessedConcurrently;
  private final InliningInfo info;
  private final InternalOptions options;
  private final int inliningInstructionLimit;
  private int instructionAllowance;

  DefaultInliningOracle(
      AppView<? extends AppInfoWithLiveness> appView,
      Inliner inliner,
      DexEncodedMethod method,
      IRCode code,
      CallSiteInformation callSiteInformation,
      Predicate<DexEncodedMethod> isProcessedConcurrently,
      InternalOptions options,
      int inliningInstructionLimit,
      int inliningInstructionAllowance) {
    this.appView = appView;
    this.inliner = inliner;
    this.method = method;
    this.code = code;
    this.callSiteInformation = callSiteInformation;
    this.isProcessedConcurrently = isProcessedConcurrently;
    info = Log.ENABLED ? new InliningInfo(method) : null;
    this.options = options;
    this.inliningInstructionLimit = inliningInstructionLimit;
    this.instructionAllowance = inliningInstructionAllowance;
  }

  @Override
  public void finish() {
    if (Log.ENABLED && info != null) {
      Log.debug(getClass(), info.toString());
    }
  }

  private DexEncodedMethod validateCandidate(InvokeMethod invoke, DexType invocationContext) {
    DexEncodedMethod candidate =
        invoke.lookupSingleTarget(inliner.appView.appInfo(), invocationContext);
    if ((candidate == null)
        || (candidate.getCode() == null)
        || inliner.appView.appInfo().definitionFor(candidate.method.getHolder()).isLibraryClass()) {
      if (info != null) {
        info.exclude(invoke, "No inlinee");
      }
      return null;
    }
    // Ignore the implicit receiver argument.
    int numberOfArguments =
        invoke.arguments().size() - (invoke.isInvokeMethodWithReceiver() ? 1 : 0);
    if (numberOfArguments != candidate.method.getArity()) {
      if (info != null) {
        info.exclude(invoke, "Argument number mismatch");
      }
      return null;
    }
    return candidate;
  }

  private Reason computeInliningReason(DexEncodedMethod target) {
    if (target.getOptimizationInfo().forceInline()
        || (inliner.appView.appInfo().hasLiveness()
            && inliner.appView.withLiveness().appInfo().forceInline.contains(target.method))) {
      return Reason.FORCE;
    }
    if (inliner.appView.appInfo().hasLiveness()
        && inliner.appView.withLiveness().appInfo().alwaysInline.contains(target.method)) {
      return Reason.ALWAYS;
    }
    if (callSiteInformation.hasSingleCallSite(target)) {
      return Reason.SINGLE_CALLER;
    }
    if (isDoubleInliningTarget(target)) {
      return Reason.DUAL_CALLER;
    }
    return Reason.SIMPLE;
  }

  private boolean canInlineStaticInvoke(DexEncodedMethod method, DexEncodedMethod target) {
    // Only proceed with inlining a static invoke if:
    // - the holder for the target equals the holder for the method, or
    // - the target method always triggers class initialization of its holder before any other side
    //   effect (hence preserving class initialization semantics).
    // - there is no non-trivial class initializer.
    DexType targetHolder = target.method.getHolder();
    if (method.method.getHolder() == targetHolder) {
      return true;
    }
    DexClass clazz = inliner.appView.appInfo().definitionFor(targetHolder);
    assert clazz != null;
    if (target.getOptimizationInfo().triggersClassInitBeforeAnySideEffect()) {
      return true;
    }
    // Check for class initializer side effects when loading this class, as inlining might remove
    // the load operation.
    //
    // See https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-5.html#jvms-5.5.
    //
    // For simplicity, we are conservative and consider all interfaces, not only the ones with
    // default methods.
    return !clazz.classInitializationMayHaveSideEffects(appView.appInfo());
  }

  private synchronized boolean isDoubleInliningTarget(DexEncodedMethod candidate) {
    // 10 is found from measuring.
    return inliner.isDoubleInliningTarget(callSiteInformation, candidate)
        && candidate.getCode().estimatedSizeForInliningAtMost(10);
  }

  private boolean passesInliningConstraints(InvokeMethod invoke, DexEncodedMethod candidate,
      Reason reason) {
    if (candidate.getOptimizationInfo().neverInline()) {
      return false;
    }

    if (method == candidate) {
      // Cannot handle recursive inlining at this point.
      // Force inlined method should never be recursive.
      assert !candidate.getOptimizationInfo().forceInline();
      if (info != null) {
        info.exclude(invoke, "direct recursion");
      }
      return false;
    }

    if (reason != Reason.FORCE && isProcessedConcurrently.test(candidate)) {
      if (info != null) {
        info.exclude(invoke, "is processed in parallel");
      }
      return false;
    }

    if (options.testing.validInliningReasons != null
        && !options.testing.validInliningReasons.contains(reason)) {
      return false;
    }

    // Abort inlining attempt if method -> target access is not right.
    if (!inliner.hasInliningAccess(method, candidate)) {
      if (info != null) {
        info.exclude(invoke, "target does not have right access");
      }
      return false;
    }

    DexClass holder = inliner.appView.appInfo().definitionFor(candidate.method.getHolder());
    if (holder.isInterface()) {
      // Art978_virtual_interfaceTest correctly expects an IncompatibleClassChangeError exception at
      // runtime.
      if (info != null) {
        info.exclude(invoke, "Do not inline target if method holder is an interface class");
      }
      return false;
    }

    if (holder.isLibraryClass()) {
      // Library functions should not be inlined.
      return false;
    }

    // Don't inline if target is synchronized.
    if (candidate.accessFlags.isSynchronized()) {
      if (info != null) {
        info.exclude(invoke, "target is synchronized");
      }
      return false;
    }

    // Attempt to inline a candidate that is only called twice.
    if ((reason == Reason.DUAL_CALLER) && (inliner.doubleInlining(method, candidate) == null)) {
      if (info != null) {
        info.exclude(invoke, "target is not ready for double inlining");
      }
      return false;
    }

    if (reason == Reason.SIMPLE) {
      // If we are looking for a simple method, only inline if actually simple.
      Code code = candidate.getCode();
      int instructionLimit = computeInstructionLimit(invoke, candidate);
      if (!code.estimatedSizeForInliningAtMost(instructionLimit)) {
        return false;
      }
    }
    return true;
  }

  private int computeInstructionLimit(InvokeMethod invoke, DexEncodedMethod candidate) {
    int instructionLimit = inliningInstructionLimit;
    BitSet hints = candidate.getOptimizationInfo().getNonNullParamHints();
    if (hints != null) {
      List<Value> arguments = invoke.inValues();
      if (invoke.isInvokeMethodWithReceiver()) {
        arguments = arguments.subList(1, arguments.size());
      }
      for (int index = 0; index < arguments.size(); index++) {
        Value argument = arguments.get(index);
        if (argument.isNeverNull() && hints.get(index)) {
          // 5-4 instructions per parameter check are expected to be removed.
          instructionLimit += 4;
        }
      }
    }
    return instructionLimit;
  }

  @Override
  public InlineAction computeForInvokeWithReceiver(
      InvokeMethodWithReceiver invoke, DexType invocationContext) {
    DexEncodedMethod candidate = validateCandidate(invoke, invocationContext);
    if (candidate == null || inliner.isBlackListed(candidate)) {
      return null;
    }

    // We can only inline an instance method call if we preserve the null check semantic (which
    // would throw NullPointerException if the receiver is null). Therefore we can inline only if
    // one of the following conditions is true:
    // * the candidate inlinee checks null receiver before any side effect
    // * the receiver is known to be non-null
    boolean receiverIsNeverNull = !invoke.getReceiver().getTypeLattice().isNullable();
    if (!receiverIsNeverNull
        && !candidate.getOptimizationInfo().checksNullReceiverBeforeAnySideEffect()) {
      if (info != null) {
        info.exclude(invoke, "receiver for candidate can be null");
      }
      assert !inliner.appView.appInfo().forceInline.contains(candidate.method);
      return null;
    }

    Reason reason = computeInliningReason(candidate);
    if (!candidate.isInliningCandidate(method, reason, inliner.appView.appInfo())) {
      // Abort inlining attempt if the single target is not an inlining candidate.
      if (info != null) {
        info.exclude(invoke, "target is not identified for inlining");
      }
      return null;
    }

    if (!passesInliningConstraints(invoke, candidate, reason)) {
      return null;
    }

    if (info != null) {
      info.include(invoke.getType(), candidate);
    }
    return new InlineAction(candidate, invoke, reason);
  }

  @Override
  public InlineAction computeForInvokeStatic(InvokeStatic invoke, DexType invocationContext) {
    DexEncodedMethod candidate = validateCandidate(invoke, invocationContext);
    if (candidate == null || inliner.isBlackListed(candidate)) {
      return null;
    }

    Reason reason = computeInliningReason(candidate);
    // Determine if this should be inlined no matter how big it is.
    if (!candidate.isInliningCandidate(method, reason, inliner.appView.appInfo())) {
      // Abort inlining attempt if the single target is not an inlining candidate.
      if (info != null) {
        info.exclude(invoke, "target is not identified for inlining");
      }
      return null;
    }

    // Abort inlining attempt if we can not guarantee class for static target has been initialized.
    if (!canInlineStaticInvoke(method, candidate)) {
      if (info != null) {
        info.exclude(invoke, "target is static but we cannot guarantee class has been initialized");
      }
      return null;
    }

    if (!passesInliningConstraints(invoke, candidate, reason)) {
      return null;
    }

    if (info != null) {
      info.include(invoke.getType(), candidate);
    }
    return new InlineAction(candidate, invoke, reason);
  }

  @Override
  public InlineAction computeForInvokePolymorphic(
      InvokePolymorphic invoke, DexType invocationContext) {
    // TODO: No inlining of invoke polymorphic for now.
    if (info != null) {
      info.exclude(invoke, "inlining through invoke signature polymorpic is not supported");
    }
    return null;
  }

  @Override
  public void ensureMethodProcessed(DexEncodedMethod target, IRCode inlinee) {
    if (!target.isProcessed()) {
      if (Log.ENABLED) {
        Log.verbose(getClass(), "Forcing extra inline on " + target.toSourceString());
      }
      inliner.performInlining(
          target, inlinee, isProcessedConcurrently, callSiteInformation);
    }
  }

  @Override
  public boolean isValidTarget(InvokeMethod invoke, DexEncodedMethod target, IRCode inlinee) {
    return !target.isInstanceInitializer()
        || inliner.legalConstructorInline(method, invoke, inlinee);
  }

  @Override
  public boolean exceededAllowance() {
    return instructionAllowance < 0;
  }

  @Override
  public void markInlined(IRCode inlinee) {
    instructionAllowance -= inliner.numberOfInstructions(inlinee);
  }

  @Override
  public void updateTypeInformationIfNeeded(
      IRCode inlinee, ListIterator<BasicBlock> blockIterator, BasicBlock block) {
    if (inliner.options.enableNonNullTracking) {
      BasicBlock state = IteratorUtils.peekNext(blockIterator);
      // Move the cursor back to where the first inlinee block was added.
      while (blockIterator.hasPrevious() && blockIterator.previous() != block) {
        // Do nothing.
      }
      assert IteratorUtils.peekNext(blockIterator) == block;

      // Kick off the tracker to add non-null IRs only to the inlinee blocks.
      Set<Value> nonNullValues =
          new NonNullTracker(appView.appInfo())
              .addNonNullInPart(code, blockIterator, inlinee.blocks::contains);
      assert !blockIterator.hasNext();

      // Restore the old state of the iterator.
      while (blockIterator.hasPrevious() && blockIterator.previous() != state) {
        // Do nothing.
      }
      assert IteratorUtils.peekNext(blockIterator) == state;
      // TODO(b/72693244): could be done when Value is created.
      new TypeAnalysis(inliner.appView.appInfo(), code.method).narrowing(nonNullValues);
    }
    // TODO(b/72693244): need a test where refined env in inlinee affects the caller.
  }

  @Override
  public DexType getReceiverTypeIfKnown(InvokeMethod invoke) {
    return null; // Maybe improve later.
  }
}
