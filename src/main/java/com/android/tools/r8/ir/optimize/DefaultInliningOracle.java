// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ClassHierarchy;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;
import com.android.tools.r8.ir.code.InvokePolymorphic;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.CallSiteInformation;
import com.android.tools.r8.ir.optimize.Inliner.InlineAction;
import com.android.tools.r8.ir.optimize.Inliner.InlineeWithReason;
import com.android.tools.r8.ir.optimize.Inliner.Reason;
import com.android.tools.r8.logging.Log;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.MainDexDirectReferenceTracer;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.IteratorUtils;
import java.util.BitSet;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Predicate;

public final class DefaultInliningOracle implements InliningOracle, InliningStrategy {

  private final AppView<AppInfoWithLiveness> appView;
  private final Inliner inliner;
  private final DexEncodedMethod method;
  private final IRCode code;
  private final CallSiteInformation callSiteInformation;
  private final Predicate<DexEncodedMethod> isProcessedConcurrently;
  private final InliningInfo info;
  private final int inliningInstructionLimit;
  private int instructionAllowance;

  DefaultInliningOracle(
      AppView<AppInfoWithLiveness> appView,
      Inliner inliner,
      DexEncodedMethod method,
      IRCode code,
      CallSiteInformation callSiteInformation,
      Predicate<DexEncodedMethod> isProcessedConcurrently,
      int inliningInstructionLimit,
      int inliningInstructionAllowance) {
    this.appView = appView;
    this.inliner = inliner;
    this.method = method;
    this.code = code;
    this.callSiteInformation = callSiteInformation;
    this.isProcessedConcurrently = isProcessedConcurrently;
    info = Log.ENABLED ? new InliningInfo(method) : null;
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
    DexEncodedMethod candidate = invoke.lookupSingleTarget(inliner.appView, invocationContext);
    if ((candidate == null)
        || (candidate.getCode() == null)
        || inliner.appView.definitionFor(candidate.method.holder).isNotProgramClass()) {
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
      assert !appView.appInfo().neverInline.contains(target.method);
      return Reason.FORCE;
    }
    if (inliner.appView.appInfo().hasLiveness()
        && inliner.appView.withLiveness().appInfo().alwaysInline.contains(target.method)) {
      return Reason.ALWAYS;
    }
    if (callSiteInformation.hasSingleCallSite(target.method)) {
      return Reason.SINGLE_CALLER;
    }
    if (isDoubleInliningTarget(target)) {
      return Reason.DUAL_CALLER;
    }
    return Reason.SIMPLE;
  }

  private boolean canInlineStaticInvoke(
      InvokeStatic invoke,
      DexEncodedMethod method,
      DexEncodedMethod target,
      ClassInitializationAnalysis classInitializationAnalysis) {
    // Only proceed with inlining a static invoke if:
    // - the holder for the target is a subtype of the holder for the method,
    // - the target method always triggers class initialization of its holder before any other side
    //   effect (hence preserving class initialization semantics),
    // - the current method has already triggered the holder for the target method to be
    //   initialized, or
    // - there is no non-trivial class initializer.
    DexType targetHolder = target.method.holder;
    if (appView.appInfo().isSubtype(method.method.holder, targetHolder)) {
      return true;
    }
    DexClass clazz = inliner.appView.definitionFor(targetHolder);
    assert clazz != null;
    if (target.getOptimizationInfo().triggersClassInitBeforeAnySideEffect()) {
      return true;
    }
    if (classInitializationAnalysis.isClassDefinitelyLoadedBeforeInstruction(
        target.method.holder, invoke)) {
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

    // We should never even try to inline something that is processed concurrently. It can lead
    // to non-deterministic behaviour as the inlining IR could be built from either original output
    // or optimized code. Right now this happens for the class class staticizer, as it just
    // processes all relevant methods in parallel with the full optimization pipeline enabled.
    // TODO(sgjesse): Add this assert "assert !isProcessedConcurrently.test(candidate);"
    if (reason != Reason.FORCE && isProcessedConcurrently.test(candidate)) {
      if (info != null) {
        info.exclude(invoke, "is processed in parallel");
      }
      return false;
    }

    InternalOptions options = appView.options();
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

    DexClass holder = inliner.appView.definitionFor(candidate.method.holder);

    if (holder.isInterface()) {
      // Art978_virtual_interfaceTest correctly expects an IncompatibleClassChangeError exception at
      // runtime.
      if (info != null) {
        info.exclude(invoke, "Do not inline target if method holder is an interface class");
      }
      return false;
    }

    if (holder.isNotProgramClass()) {
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
        if (info != null) {
          info.exclude(
              invoke,
              "instruction limit exceeds: "
                  + code.estimatedSizeForInlining()
                  + " <= "
                  + instructionLimit);
        }
        return false;
      }
    }

    if (!inliner.mainDexClasses.isEmpty()) {
      // Don't inline code with references beyond root main dex classes into a root main dex class.
      // If we do this it can increase the size of the main dex dependent classes.
      if (inliner.mainDexClasses.getRoots().contains(method.method.holder)
          && MainDexDirectReferenceTracer.hasReferencesOutsideFromCode(
          appView.appInfo(), candidate, inliner.mainDexClasses.getRoots())) {
        if (info != null) {
          info.exclude(invoke, "target has references beyond main dex");
        }
        return false;
      }
      // Allow inlining into the classes in the main dex dependent set without restrictions.
    }

    return true;
  }

  private int computeInstructionLimit(InvokeMethod invoke, DexEncodedMethod candidate) {
    int instructionLimit = inliningInstructionLimit;
    BitSet hints = candidate.getOptimizationInfo().getNonNullParamOrThrow();
    if (hints != null) {
      List<Value> arguments = invoke.inValues();
      if (invoke.isInvokeMethodWithReceiver()) {
        arguments = arguments.subList(1, arguments.size());
      }
      for (int index = 0; index < arguments.size(); index++) {
        Value argument = arguments.get(index);
        if ((argument.isArgument()
            || (argument.getTypeLattice().isReference() && argument.isNeverNull()))
            && hints.get(index)) {
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
    if (candidate == null || inliner.isBlackListed(candidate.method)) {
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

    InlineAction action = new InlineAction(candidate, invoke, reason);

    if (invoke.getReceiver().getTypeLattice().isDefinitelyNull()) {
      action.setShouldReturnEmptyThrowingCode();
    } else {
      // When inlining an instance method call, we need to preserve the null check for the receiver.
      // Therefore, if the receiver may be null and the candidate inlinee does not throw if the
      // receiver is null before any other side effect, then we must synthesize a null check.
      if (invoke.getReceiver().getTypeLattice().isNullable()
          && !candidate.getOptimizationInfo().checksNullReceiverBeforeAnySideEffect()) {
        if (!appView.options().enableInliningOfInvokesWithNullableReceivers) {
          return null;
        }
        action.setShouldSynthesizeNullCheckForReceiver();
      }
    }

    return action;
  }

  @Override
  public InlineAction computeForInvokeStatic(
      InvokeStatic invoke,
      DexType invocationContext,
      ClassInitializationAnalysis classInitializationAnalysis) {
    DexEncodedMethod candidate = validateCandidate(invoke, invocationContext);
    if (candidate == null || inliner.isBlackListed(candidate.method)) {
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
    if (!canInlineStaticInvoke(invoke, method, candidate, classInitializationAnalysis)) {
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
  public boolean isValidTarget(
      InvokeMethod invoke, DexEncodedMethod target, IRCode inlinee, ClassHierarchy hierarchy) {
    return !target.isInstanceInitializer()
        || inliner.legalConstructorInline(method, invoke, inlinee, hierarchy);
  }

  @Override
  public boolean stillHasBudget() {
    return instructionAllowance > 0;
  }

  @Override
  public boolean willExceedBudget(InlineeWithReason inlinee, BasicBlock block) {
    if (inlinee.reason.mustBeInlined()) {
      return false;
    }

    if (block.hasCatchHandlers() && inlinee.reason != Reason.FORCE) {
      // Inlining could lead to an explosion of move-exception and resolution moves. As an
      // example, consider the following piece of code.
      //   try {
      //     ...
      //     foo();
      //     ...
      //   } catch (A e) { ... }
      //   } catch (B e) { ... }
      //   } catch (C e) { ... }
      //
      // The generated code for the above example will have a move-exception instruction
      // for each of the three catch handlers. Furthermore, the blocks with these move-
      // exception instructions may require a number of resolution moves to setup the
      // register state for the catch handlers. When inlining foo(), the generated code
      // will have a move-exception instruction *for each of the instructions in foo()
      // that can throw*, along with the necessary resolution moves for each exception-
      // edge. We therefore abort inlining if the number of exception-edges explode.
      int numberOfThrowingInstructionsInInlinee = 0;
      for (BasicBlock inlineeBlock : inlinee.code.blocks) {
        numberOfThrowingInstructionsInInlinee += inlineeBlock.numberOfThrowingInstructions();
      }
      // Estimate the number of "control flow resolution blocks", where we will insert a
      // move-exception instruction (if needed), along with all the resolution moves that
      // will be needed to setup the register state for the catch handler.
      int estimatedNumberOfControlFlowResolutionBlocks =
          numberOfThrowingInstructionsInInlinee * block.numberOfCatchHandlers();
      // Abort if inlining could lead to an explosion in the number of control flow
      // resolution blocks that setup the register state before the actual catch handler.
      if (estimatedNumberOfControlFlowResolutionBlocks
          >= appView.options().inliningControlFlowResolutionBlocksThreshold) {
        return true;
      }
    }

    return instructionAllowance < Inliner.numberOfInstructions(inlinee.code);
  }

  @Override
  public void markInlined(InlineeWithReason inlinee) {
    // TODO(118734615): All inlining use from the budget - should that only be SIMPLE?
    instructionAllowance -= Inliner.numberOfInstructions(inlinee.code);
  }

  @Override
  public void updateTypeInformationIfNeeded(
      IRCode inlinee, ListIterator<BasicBlock> blockIterator, BasicBlock block) {
    if (appView.options().enableNonNullTracking) {
      BasicBlock state = IteratorUtils.peekNext(blockIterator);
      // Move the cursor back to where the first inlinee block was added.
      while (blockIterator.hasPrevious() && blockIterator.previous() != block) {
        // Do nothing.
      }
      assert IteratorUtils.peekNext(blockIterator) == block;

      // Kick off the tracker to add non-null IRs only to the inlinee blocks.
      new NonNullTracker(appView).addNonNullInPart(code, blockIterator, inlinee.blocks::contains);
      assert !blockIterator.hasNext();

      // Restore the old state of the iterator.
      while (blockIterator.hasPrevious() && blockIterator.previous() != state) {
        // Do nothing.
      }
      assert IteratorUtils.peekNext(blockIterator) == state;
    }
    // TODO(b/72693244): need a test where refined env in inlinee affects the caller.
  }

  @Override
  public DexType getReceiverTypeIfKnown(InvokeMethod invoke) {
    return null; // Maybe improve later.
  }
}
