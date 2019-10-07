// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstancePut;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.CallSiteInformation;
import com.android.tools.r8.ir.optimize.Inliner.InlineAction;
import com.android.tools.r8.ir.optimize.Inliner.InlineeWithReason;
import com.android.tools.r8.ir.optimize.Inliner.Reason;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.ir.optimize.inliner.WhyAreYouNotInliningReporter;
import com.android.tools.r8.logging.Log;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.MainDexDirectReferenceTracer;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.IteratorUtils;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class DefaultInliningOracle implements InliningOracle, InliningStrategy {

  private final AppView<AppInfoWithLiveness> appView;
  private final Inliner inliner;
  private final DexEncodedMethod method;
  private final IRCode code;
  private final CallSiteInformation callSiteInformation;
  private final Predicate<DexEncodedMethod> isProcessedConcurrently;
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
    this.inliningInstructionLimit = inliningInstructionLimit;
    this.instructionAllowance = inliningInstructionAllowance;
  }

  @Override
  public boolean isForcedInliningOracle() {
    return false;
  }

  private boolean isSingleTargetInvalid(
      InvokeMethod invoke,
      DexEncodedMethod singleTarget,
      WhyAreYouNotInliningReporter whyAreYouNotInliningReporter) {
    if (singleTarget == null) {
      throw new Unreachable();
    }

    if (!singleTarget.hasCode()) {
      whyAreYouNotInliningReporter.reportUnknownReason();
      return true;
    }

    if (appView.definitionFor(singleTarget.method.holder).isNotProgramClass()) {
      whyAreYouNotInliningReporter.reportUnknownReason();
      return true;
    }

    // Ignore the implicit receiver argument.
    int numberOfArguments =
        invoke.arguments().size() - BooleanUtils.intValue(invoke.isInvokeMethodWithReceiver());
    if (numberOfArguments != singleTarget.method.getArity()) {
      whyAreYouNotInliningReporter.reportUnknownReason();
      return true;
    }

    return false;
  }

  private Reason computeInliningReason(DexEncodedMethod target) {
    if (target.getOptimizationInfo().forceInline()
        || (appView.appInfo().hasLiveness()
            && appView.withLiveness().appInfo().forceInline.contains(target.method))) {
      assert !appView.appInfo().neverInline.contains(target.method);
      return Reason.FORCE;
    }
    if (appView.appInfo().hasLiveness()
        && appView.withLiveness().appInfo().alwaysInline.contains(target.method)) {
      return Reason.ALWAYS;
    }
    if (appView.options().disableInliningOfLibraryMethodOverrides
        && target.isLibraryMethodOverride().isTrue()) {
      // This method will always have an implicit call site from the library, so we won't be able to
      // remove it after inlining even if we have single or dual call site information from the
      // program.
      return Reason.SIMPLE;
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
      ClassInitializationAnalysis classInitializationAnalysis,
      WhyAreYouNotInliningReporter whyAreYouNotInliningReporter) {
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
    DexClass clazz = appView.definitionFor(targetHolder);
    assert clazz != null;
    if (target.getOptimizationInfo().triggersClassInitBeforeAnySideEffect()) {
      return true;
    }
    if (!method.isStatic()) {
      boolean targetIsGuaranteedToBeInitialized =
          appView.withInitializedClassesInInstanceMethods(
              analysis ->
                  analysis.isClassDefinitelyLoadedInInstanceMethodsOn(
                      target.method.holder, method.method.holder),
              false);
      if (targetIsGuaranteedToBeInitialized) {
        return true;
      }
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
    if (!clazz.classInitializationMayHaveSideEffects(appView)) {
      return true;
    }

    whyAreYouNotInliningReporter.reportUnknownReason();
    return false;
  }

  private synchronized boolean isDoubleInliningTarget(DexEncodedMethod candidate) {
    // 10 is found from measuring.
    return inliner.isDoubleInliningTarget(callSiteInformation, candidate)
        && candidate.getCode().estimatedSizeForInliningAtMost(10);
  }

  private boolean passesInliningConstraints(
      InvokeMethod invoke,
      DexEncodedMethod candidate,
      Reason reason,
      WhyAreYouNotInliningReporter whyAreYouNotInliningReporter) {
    if (candidate.getOptimizationInfo().neverInline()) {
      whyAreYouNotInliningReporter.reportUnknownReason();
      return false;
    }

    // We don't inline into constructors when producing class files since this can mess up
    // the stackmap, see b/136250031
    if (method.isInstanceInitializer()
        && appView.options().isGeneratingClassFiles()
        && reason != Reason.FORCE) {
      whyAreYouNotInliningReporter.reportUnknownReason();
      return false;
    }

    if (method == candidate) {
      // Cannot handle recursive inlining at this point.
      // Force inlined method should never be recursive.
      assert !candidate.getOptimizationInfo().forceInline();
      whyAreYouNotInliningReporter.reportUnknownReason();
      return false;
    }

    // We should never even try to inline something that is processed concurrently. It can lead
    // to non-deterministic behaviour as the inlining IR could be built from either original output
    // or optimized code. Right now this happens for the class class staticizer, as it just
    // processes all relevant methods in parallel with the full optimization pipeline enabled.
    // TODO(sgjesse): Add this assert "assert !isProcessedConcurrently.test(candidate);"
    if (reason != Reason.FORCE && isProcessedConcurrently.test(candidate)) {
      whyAreYouNotInliningReporter.reportUnknownReason();
      return false;
    }

    InternalOptions options = appView.options();
    if (options.featureSplitConfiguration != null
        && !options.featureSplitConfiguration.inSameFeatureOrBase(
            candidate.method, method.method)) {
      whyAreYouNotInliningReporter.reportUnknownReason();
      return false;
    }

    if (options.testing.validInliningReasons != null
        && !options.testing.validInliningReasons.contains(reason)) {
      whyAreYouNotInliningReporter.reportUnknownReason();
      return false;
    }

    // Abort inlining attempt if method -> target access is not right.
    if (!inliner.hasInliningAccess(method, candidate)) {
      whyAreYouNotInliningReporter.reportUnknownReason();
      return false;
    }

    DexClass holder = appView.definitionFor(candidate.method.holder);

    if (holder.isInterface()) {
      // Art978_virtual_interfaceTest correctly expects an IncompatibleClassChangeError exception at
      // runtime.
      whyAreYouNotInliningReporter.reportUnknownReason();
      return false;
    }

    if (holder.isNotProgramClass()) {
      whyAreYouNotInliningReporter.reportUnknownReason();
      return false;
    }

    // Don't inline if target is synchronized.
    if (candidate.accessFlags.isSynchronized()) {
      whyAreYouNotInliningReporter.reportUnknownReason();
      return false;
    }

    if (reason == Reason.DUAL_CALLER) {
      if (satisfiesRequirementsForSimpleInlining(invoke, candidate)) {
        // When we have a method with two call sites, we simply inline the method as we normally do
        // when the method is small. We still need to ensure that the other call site is also
        // inlined, though. Therefore, we record here that we have seen one of the two call sites
        // as we normally do.
        inliner.recordDoubleInliningCandidate(method, candidate);
      } else {
        if (inliner.satisfiesRequirementsForDoubleInlining(method, candidate)) {
          whyAreYouNotInliningReporter.reportUnknownReason();
          return false;
        }
      }
    } else if (reason == Reason.SIMPLE
        && !satisfiesRequirementsForSimpleInlining(invoke, candidate)) {
      whyAreYouNotInliningReporter.reportUnknownReason();
      return false;
    }

    if (!inliner.mainDexClasses.isEmpty()) {
      // Don't inline code with references beyond root main dex classes into a root main dex class.
      // If we do this it can increase the size of the main dex dependent classes.
      if (inliner.mainDexClasses.getRoots().contains(method.method.holder)
          && MainDexDirectReferenceTracer.hasReferencesOutsideFromCode(
          appView.appInfo(), candidate, inliner.mainDexClasses.getRoots())) {
        whyAreYouNotInliningReporter.reportUnknownReason();
        return false;
      }
      // Allow inlining into the classes in the main dex dependent set without restrictions.
    }

    return true;
  }

  private boolean satisfiesRequirementsForSimpleInlining(
      InvokeMethod invoke, DexEncodedMethod target) {
    // If we are looking for a simple method, only inline if actually simple.
    Code code = target.getCode();
    int instructionLimit = computeInstructionLimit(invoke, target);
    if (code.estimatedSizeForInliningAtMost(instructionLimit)) {
      return true;
    }
    return false;
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
  public DexEncodedMethod lookupSingleTarget(InvokeMethod invoke, DexType context) {
    return invoke.lookupSingleTarget(appView, context);
  }

  @Override
  public InlineAction computeForInvokeWithReceiver(
      InvokeMethodWithReceiver invoke,
      DexEncodedMethod singleTarget,
      DexMethod invocationContext,
      WhyAreYouNotInliningReporter whyAreYouNotInliningReporter) {
    if (isSingleTargetInvalid(invoke, singleTarget, whyAreYouNotInliningReporter)) {
      return null;
    }

    if (inliner.isBlackListed(singleTarget, whyAreYouNotInliningReporter)) {
      return null;
    }

    Reason reason = computeInliningReason(singleTarget);
    if (!singleTarget.isInliningCandidate(
        method, reason, appView.appInfo(), whyAreYouNotInliningReporter)) {
      return null;
    }

    if (!passesInliningConstraints(invoke, singleTarget, reason, whyAreYouNotInliningReporter)) {
      return null;
    }

    Value receiver = invoke.getReceiver();
    if (receiver.getTypeLattice().isDefinitelyNull()) {
      // A definitely null receiver will throw an error on call site.
      whyAreYouNotInliningReporter.reportUnknownReason();
      return null;
    }

    InlineAction action = new InlineAction(singleTarget, invoke, reason);
    if (receiver.getTypeLattice().isNullable()) {
      assert !receiver.getTypeLattice().isDefinitelyNull();
      // When inlining an instance method call, we need to preserve the null check for the
      // receiver. Therefore, if the receiver may be null and the candidate inlinee does not
      // throw if the receiver is null before any other side effect, then we must synthesize a
      // null check.
      if (!singleTarget.getOptimizationInfo().checksNullReceiverBeforeAnySideEffect()) {
        InternalOptions options = appView.options();
        if (!options.enableInliningOfInvokesWithNullableReceivers) {
          whyAreYouNotInliningReporter.reportUnknownReason();
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
      DexEncodedMethod singleTarget,
      DexMethod invocationContext,
      ClassInitializationAnalysis classInitializationAnalysis,
      WhyAreYouNotInliningReporter whyAreYouNotInliningReporter) {
    if (isSingleTargetInvalid(invoke, singleTarget, whyAreYouNotInliningReporter)) {
      return null;
    }

    if (inliner.isBlackListed(singleTarget, whyAreYouNotInliningReporter)) {
      return null;
    }

    Reason reason = computeInliningReason(singleTarget);
    // Determine if this should be inlined no matter how big it is.
    if (!singleTarget.isInliningCandidate(
        method, reason, appView.appInfo(), whyAreYouNotInliningReporter)) {
      return null;
    }

    // Abort inlining attempt if we can not guarantee class for static target has been initialized.
    if (!canInlineStaticInvoke(
        invoke, method, singleTarget, classInitializationAnalysis, whyAreYouNotInliningReporter)) {
      return null;
    }

    if (!passesInliningConstraints(invoke, singleTarget, reason, whyAreYouNotInliningReporter)) {
      return null;
    }

    return new InlineAction(singleTarget, invoke, reason);
  }

  @Override
  public void ensureMethodProcessed(
      DexEncodedMethod target, IRCode inlinee, OptimizationFeedback feedback) {
    if (!target.isProcessed()) {
      if (Log.ENABLED) {
        Log.verbose(getClass(), "Forcing extra inline on " + target.toSourceString());
      }
      inliner.performInlining(
          target, inlinee, feedback, isProcessedConcurrently, callSiteInformation);
    }
  }

  @Override
  public boolean canInlineInstanceInitializer(
      IRCode inlinee,
      WhyAreYouNotInliningReporter whyAreYouNotInliningReporter) {
    // In the Java VM Specification section "4.10.2.4. Instance Initialization Methods and
    // Newly Created Objects" it says:
    //
    // Before that method invokes another instance initialization method of myClass or its direct
    // superclass on this, the only operation the method can perform on this is assigning fields
    // declared within myClass.

    // Allow inlining a constructor into a constructor of the same class, as the constructor code
    // is expected to adhere to the VM specification.
    DexType callerMethodHolder = method.method.holder;
    DexType calleeMethodHolder = inlinee.method.method.holder;
    // Calling a constructor on the same class from a constructor can always be inlined.
    if (method.isInstanceInitializer() && callerMethodHolder == calleeMethodHolder) {
      return true;
    }

    // Only allow inlining a constructor into a non-constructor if:
    // (1) the first use of the uninitialized object is the receiver of an invoke of <init>(),
    // (2) the constructor does not initialize any final fields, as such is only allowed from within
    //     a constructor of the corresponding class, and
    // (3) the constructors own <init>() call is on the same class.
    //
    // Note that, due to (3), we do allow inlining of `A(int x)` into another class, but not the
    // default constructor `A()`, since the default constructor invokes Object.<init>().
    //
    //   class A {
    //     A() { ... }
    //     A(int x) {
    //       this()
    //       ...
    //     }
    //   }
    Value thisValue = inlinee.entryBlock().entry().asArgument().outValue();

    List<InvokeDirect> initCallsOnThis = new ArrayList<>();
    for (Instruction instruction : inlinee.instructions()) {
      if (instruction.isInvokeDirect()) {
        InvokeDirect initCall = instruction.asInvokeDirect();
        DexMethod invokedMethod = initCall.getInvokedMethod();
        if (appView.dexItemFactory().isConstructor(invokedMethod)) {
          Value receiver = initCall.getReceiver().getAliasedValue();
          if (receiver == thisValue) {
            // The <init>() call of the constructor must be on the same class.
            if (calleeMethodHolder != invokedMethod.holder) {
              whyAreYouNotInliningReporter
                  .reportUnsafeConstructorInliningDueToIndirectConstructorCall(initCall);
              return false;
            }
            initCallsOnThis.add(initCall);
          }
        }
      } else if (instruction.isInstancePut()) {
        // Final fields may not be initialized outside of a constructor in the enclosing class.
        InstancePut instancePut = instruction.asInstancePut();
        DexField field = instancePut.getField();
        DexEncodedField target = appView.appInfo().lookupInstanceTarget(field.holder, field);
        if (target == null || target.accessFlags.isFinal()) {
          whyAreYouNotInliningReporter.reportUnsafeConstructorInliningDueToFinalFieldAssignment(
              instancePut);
          return false;
        }
      }
    }

    // Check that there are no uses of the uninitialized object before it gets initialized.
    int markingColor = inlinee.reserveMarkingColor();
    for (InvokeDirect initCallOnThis : initCallsOnThis) {
      BasicBlock block = initCallOnThis.getBlock();
      for (Instruction instruction : block.instructionsBefore(initCallOnThis)) {
        for (Value inValue : instruction.inValues()) {
          Value root = inValue.getAliasedValue();
          if (root == thisValue) {
            inlinee.returnMarkingColor(markingColor);
            whyAreYouNotInliningReporter.reportUnsafeConstructorInliningDueToUninitializedObjectUse(
                instruction);
            return false;
          }
        }
      }
      for (BasicBlock predecessor : block.getPredecessors()) {
        inlinee.markTransitivePredecessors(predecessor, markingColor);
      }
    }

    for (BasicBlock block : inlinee.blocks) {
      if (block.isMarked(markingColor)) {
        for (Instruction instruction : block.getInstructions()) {
          for (Value inValue : instruction.inValues()) {
            Value root = inValue.getAliasedValue();
            if (root == thisValue) {
              inlinee.returnMarkingColor(markingColor);
              whyAreYouNotInliningReporter
                  .reportUnsafeConstructorInliningDueToUninitializedObjectUse(instruction);
              return false;
            }
          }
        }
      }
    }

    inlinee.returnMarkingColor(markingColor);
    return true;
  }

  @Override
  public boolean stillHasBudget(
      InlineAction action, WhyAreYouNotInliningReporter whyAreYouNotInliningReporter) {
    if (action.reason.mustBeInlined()) {
      return true;
    }
    boolean stillHasBudget = instructionAllowance > 0;
    if (!stillHasBudget) {
      whyAreYouNotInliningReporter.reportInstructionBudgetIsExceeded();
    }
    return stillHasBudget;
  }

  @Override
  public boolean willExceedBudget(
      InlineeWithReason inlinee,
      BasicBlock block,
      WhyAreYouNotInliningReporter whyAreYouNotInliningReporter) {
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
      int threshold = appView.options().inliningControlFlowResolutionBlocksThreshold;
      if (estimatedNumberOfControlFlowResolutionBlocks >= threshold) {
        whyAreYouNotInliningReporter
            .reportPotentialExplosionInExceptionalControlFlowResolutionBlocks(
                estimatedNumberOfControlFlowResolutionBlocks, threshold);
        return true;
      }
    }

    int numberOfInstructions = Inliner.numberOfInstructions(inlinee.code);
    if (instructionAllowance < Inliner.numberOfInstructions(inlinee.code)) {
      whyAreYouNotInliningReporter.reportWillExceedInstructionBudget(
          numberOfInstructions, instructionAllowance);
      return true;
    }

    return false;
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

      Set<BasicBlock> inlineeBlocks = Sets.newIdentityHashSet();
      inlineeBlocks.addAll(inlinee.blocks);

      // Introduce aliases only to the inlinee blocks.
      if (appView.options().testing.forceAssumeNoneInsertion) {
        insertAssumeInstructionsToInlinee(
            new AliasIntroducer(appView), code, state, blockIterator, inlineeBlocks);
      }

      // Add non-null IRs only to the inlinee blocks.
      Consumer<BasicBlock> splitBlockConsumer = inlineeBlocks::add;
      Assumer nonNullTracker = new NonNullTracker(appView, splitBlockConsumer);
      insertAssumeInstructionsToInlinee(
          nonNullTracker, code, state, blockIterator, inlineeBlocks);

      // Add dynamic type assumptions only to the inlinee blocks.
      insertAssumeInstructionsToInlinee(
          new DynamicTypeOptimization(appView), code, state, blockIterator, inlineeBlocks);
    }
    // TODO(b/72693244): need a test where refined env in inlinee affects the caller.
  }

  private void insertAssumeInstructionsToInlinee(
      Assumer assumer,
      IRCode code,
      BasicBlock state,
      ListIterator<BasicBlock> blockIterator,
      Set<BasicBlock> inlineeBlocks) {
    assumer.insertAssumeInstructionsInBlocks(code, blockIterator, inlineeBlocks::contains);
    assert !blockIterator.hasNext();

    // Restore the old state of the iterator.
    while (blockIterator.hasPrevious() && blockIterator.previous() != state) {
      // Do nothing.
    }
    assert IteratorUtils.peekNext(blockIterator) == state;
  }

  @Override
  public DexType getReceiverTypeIfKnown(InvokeMethod invoke) {
    return null; // Maybe improve later.
  }
}
