// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static com.android.tools.r8.ir.optimize.inliner.InlinerUtils.addMonitorEnterValue;
import static com.android.tools.r8.ir.optimize.inliner.InlinerUtils.collectAllMonitorEnterValues;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.features.ClassToFeatureSplitMap;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.ResolutionResult.SingleResolutionResult;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstancePut;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.Monitor;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.optimize.Inliner.InlineAction;
import com.android.tools.r8.ir.optimize.Inliner.InlineeWithReason;
import com.android.tools.r8.ir.optimize.Inliner.Reason;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.ir.optimize.inliner.InliningReasonStrategy;
import com.android.tools.r8.ir.optimize.inliner.WhyAreYouNotInliningReporter;
import com.android.tools.r8.logging.Log;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.MainDexDirectReferenceTracer;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Set;

public final class DefaultInliningOracle implements InliningOracle, InliningStrategy {

  private final AppView<AppInfoWithLiveness> appView;
  private final Inliner inliner;
  private final ProgramMethod method;
  private final MethodProcessor methodProcessor;
  private final InliningReasonStrategy reasonStrategy;
  private final int inliningInstructionLimit;
  private int instructionAllowance;

  DefaultInliningOracle(
      AppView<AppInfoWithLiveness> appView,
      Inliner inliner,
      InliningReasonStrategy inliningReasonStrategy,
      ProgramMethod method,
      MethodProcessor methodProcessor,
      int inliningInstructionLimit,
      int inliningInstructionAllowance) {
    this.appView = appView;
    this.inliner = inliner;
    this.reasonStrategy = inliningReasonStrategy;
    this.method = method;
    this.methodProcessor = methodProcessor;
    this.inliningInstructionLimit = inliningInstructionLimit;
    this.instructionAllowance = inliningInstructionAllowance;
  }

  @Override
  public boolean isForcedInliningOracle() {
    return false;
  }

  private boolean isSingleTargetInvalid(
      InvokeMethod invoke,
      ProgramMethod singleTarget,
      WhyAreYouNotInliningReporter whyAreYouNotInliningReporter) {
    if (singleTarget == null) {
      throw new Unreachable(
          "Unexpected attempt to inline invoke that does not have a single target");
    }

    if (singleTarget.getDefinition().isClassInitializer()) {
      throw new Unreachable(
          "Unexpected attempt to invoke a class initializer (`"
              + singleTarget.toSourceString()
              + "`)");
    }

    if (!singleTarget.getDefinition().hasCode()) {
      whyAreYouNotInliningReporter.reportInlineeDoesNotHaveCode();
      return true;
    }

    // Ignore the implicit receiver argument.
    int numberOfArguments =
        invoke.arguments().size() - BooleanUtils.intValue(invoke.isInvokeMethodWithReceiver());
    int arity = singleTarget.getReference().getArity();
    if (numberOfArguments != arity) {
      whyAreYouNotInliningReporter.reportIncorrectArity(numberOfArguments, arity);
      return true;
    }

    return false;
  }

  @Override
  public boolean passesInliningConstraints(
      InvokeMethod invoke,
      SingleResolutionResult resolutionResult,
      ProgramMethod singleTarget,
      Reason reason,
      WhyAreYouNotInliningReporter whyAreYouNotInliningReporter) {
    DexEncodedMethod singleTargetMethod = singleTarget.getDefinition();
    if (singleTargetMethod.getOptimizationInfo().neverInline()) {
      whyAreYouNotInliningReporter.reportMarkedAsNeverInline();
      return false;
    }

    // We don't inline into constructors when producing class files since this can mess up
    // the stackmap, see b/136250031
    if (method.getDefinition().isInstanceInitializer()
        && appView.options().isGeneratingClassFiles()
        && reason != Reason.FORCE) {
      whyAreYouNotInliningReporter.reportNoInliningIntoConstructorsWhenGeneratingClassFiles();
      return false;
    }

    if (method.getDefinition() == singleTargetMethod) {
      // Cannot handle recursive inlining at this point.
      // Force inlined method should never be recursive.
      assert !singleTargetMethod.getOptimizationInfo().forceInline();
      whyAreYouNotInliningReporter.reportRecursiveMethod();
      return false;
    }

    // We should never even try to inline something that is processed concurrently. It can lead
    // to non-deterministic behaviour as the inlining IR could be built from either original output
    // or optimized code. Right now this happens for the class class staticizer, as it just
    // processes all relevant methods in parallel with the full optimization pipeline enabled.
    // TODO(sgjesse): Add this assert "assert !isProcessedConcurrently.test(candidate);"
    if (reason != Reason.FORCE && methodProcessor.isProcessedConcurrently(singleTarget)) {
      whyAreYouNotInliningReporter.reportProcessedConcurrently();
      return false;
    }

    ClassToFeatureSplitMap classToFeatureSplitMap = appView.appInfo().getClassToFeatureSplitMap();
    if (!classToFeatureSplitMap.isInSameFeatureOrBothInBase(singleTarget, method)) {
      // Still allow inlining if we inline from the base into a feature.
      if (!classToFeatureSplitMap.isInBase(singleTarget.getHolder())) {
        whyAreYouNotInliningReporter.reportInliningAcrossFeatureSplit();
        return false;
      }
    }

    Set<Reason> validInliningReasons = appView.options().testing.validInliningReasons;
    if (validInliningReasons != null && !validInliningReasons.contains(reason)) {
      whyAreYouNotInliningReporter.reportInvalidInliningReason(reason, validInliningReasons);
      return false;
    }

    // Abort inlining attempt if method -> target access is not right.
    if (resolutionResult.isAccessibleFrom(method, appView.appInfo()).isPossiblyFalse()) {
      whyAreYouNotInliningReporter.reportInaccessible();
      return false;
    }

    if (reason == Reason.DUAL_CALLER) {
      if (satisfiesRequirementsForSimpleInlining(invoke, singleTarget)) {
        // When we have a method with two call sites, we simply inline the method as we normally do
        // when the method is small. We still need to ensure that the other call site is also
        // inlined, though. Therefore, we record here that we have seen one of the two call sites
        // as we normally do.
        inliner.recordDoubleInliningCandidate(method, singleTarget);
      } else if (inliner.isDoubleInliningEnabled()) {
        if (!inliner.satisfiesRequirementsForDoubleInlining(method, singleTarget)) {
          whyAreYouNotInliningReporter.reportInvalidDoubleInliningCandidate();
          return false;
        }
      } else {
        // TODO(b/142300882): Should in principle disallow inlining in this case.
        inliner.recordDoubleInliningCandidate(method, singleTarget);
      }
    } else if (reason == Reason.SIMPLE
        && !satisfiesRequirementsForSimpleInlining(invoke, singleTarget)) {
      whyAreYouNotInliningReporter.reportInlineeNotSimple();
      return false;
    }

    // Don't inline code with references beyond root main dex classes into a root main dex class.
    // If we do this it can increase the size of the main dex dependent classes.
    if (reason != Reason.FORCE
        && inlineeRefersToClassesNotInMainDex(method.getHolderType(), singleTarget)) {
      whyAreYouNotInliningReporter.reportInlineeRefersToClassesNotInMainDex();
      return false;
    }
    assert reason != Reason.FORCE
        || !inlineeRefersToClassesNotInMainDex(method.getHolderType(), singleTarget);
    return true;
  }

  private boolean inlineeRefersToClassesNotInMainDex(DexType holder, ProgramMethod target) {
    if (inliner.mainDexClasses.isEmpty() || !inliner.mainDexClasses.getRoots().contains(holder)) {
      return false;
    }
    return MainDexDirectReferenceTracer.hasReferencesOutsideFromCode(
        appView.appInfo(), target, inliner.mainDexClasses.getRoots());
  }

  private boolean satisfiesRequirementsForSimpleInlining(
      InvokeMethod invoke, ProgramMethod target) {
    // If we are looking for a simple method, only inline if actually simple.
    Code code = target.getDefinition().getCode();
    int instructionLimit = computeInstructionLimit(invoke, target);
    if (code.estimatedSizeForInliningAtMost(instructionLimit)) {
      return true;
    }
    return false;
  }

  private int computeInstructionLimit(InvokeMethod invoke, ProgramMethod candidate) {
    int instructionLimit = inliningInstructionLimit;
    BitSet hints = candidate.getDefinition().getOptimizationInfo().getNonNullParamOrThrow();
    if (hints != null) {
      List<Value> arguments = invoke.inValues();
      if (invoke.isInvokeMethodWithReceiver()) {
        arguments = arguments.subList(1, arguments.size());
      }
      for (int index = 0; index < arguments.size(); index++) {
        Value argument = arguments.get(index);
        if ((argument.isArgument()
                || (argument.getType().isReferenceType() && argument.isNeverNull()))
            && hints.get(index)) {
          // 5-4 instructions per parameter check are expected to be removed.
          instructionLimit += 4;
        }
      }
    }
    return instructionLimit;
  }

  @Override
  public ProgramMethod lookupSingleTarget(InvokeMethod invoke, ProgramMethod context) {
    return invoke.lookupSingleProgramTarget(appView, context);
  }

  @Override
  public InlineAction computeInlining(
      InvokeMethod invoke,
      SingleResolutionResult resolutionResult,
      ProgramMethod singleTarget,
      ProgramMethod context,
      ClassInitializationAnalysis classInitializationAnalysis,
      WhyAreYouNotInliningReporter whyAreYouNotInliningReporter) {
    if (isSingleTargetInvalid(invoke, singleTarget, whyAreYouNotInliningReporter)) {
      return null;
    }

    if (inliner.neverInline(invoke, resolutionResult, singleTarget, whyAreYouNotInliningReporter)) {
      return null;
    }

    Reason reason = reasonStrategy.computeInliningReason(invoke, singleTarget, context);
    if (reason == Reason.NEVER) {
      return null;
    }

    if (!singleTarget
        .getDefinition()
        .isInliningCandidate(method, reason, appView.appInfo(), whyAreYouNotInliningReporter)) {
      return null;
    }

    if (!passesInliningConstraints(
        invoke, resolutionResult, singleTarget, reason, whyAreYouNotInliningReporter)) {
      return null;
    }

    return invoke.computeInlining(
        singleTarget, reason, this, classInitializationAnalysis, whyAreYouNotInliningReporter);
  }

  public InlineAction computeForInvokeWithReceiver(
      InvokeMethodWithReceiver invoke,
      ProgramMethod singleTarget,
      Reason reason,
      WhyAreYouNotInliningReporter whyAreYouNotInliningReporter) {
    Value receiver = invoke.getReceiver();
    if (receiver.getType().isDefinitelyNull()) {
      // A definitely null receiver will throw an error on call site.
      whyAreYouNotInliningReporter.reportReceiverDefinitelyNull();
      return null;
    }

    InlineAction action = new InlineAction(singleTarget, invoke, reason);
    if (receiver.getType().isNullable()) {
      assert !receiver.getType().isDefinitelyNull();
      // When inlining an instance method call, we need to preserve the null check for the
      // receiver. Therefore, if the receiver may be null and the candidate inlinee does not
      // throw if the receiver is null before any other side effect, then we must synthesize a
      // null check.
      if (!singleTarget
          .getDefinition()
          .getOptimizationInfo()
          .checksNullReceiverBeforeAnySideEffect()) {
        InternalOptions options = appView.options();
        if (!options.enableInliningOfInvokesWithNullableReceivers) {
          whyAreYouNotInliningReporter.reportReceiverMaybeNull();
          return null;
        }
        action.setShouldSynthesizeNullCheckForReceiver();
      }
    }
    return action;
  }

  public InlineAction computeForInvokeStatic(
      InvokeStatic invoke,
      ProgramMethod singleTarget,
      Reason reason,
      ClassInitializationAnalysis classInitializationAnalysis,
      WhyAreYouNotInliningReporter whyAreYouNotInliningReporter) {
    InlineAction action = new InlineAction(singleTarget, invoke, reason);
    if (isTargetClassInitialized(invoke, method, singleTarget, classInitializationAnalysis)) {
      return action;
    }
    if (appView.canUseInitClass()
        && appView.options().enableInliningOfInvokesWithClassInitializationSideEffects) {
      action.setShouldSynthesizeInitClass();
      return action;
    }
    whyAreYouNotInliningReporter.reportMustTriggerClassInitialization();
    return null;
  }

  private boolean isTargetClassInitialized(
      InvokeStatic invoke,
      ProgramMethod context,
      ProgramMethod target,
      ClassInitializationAnalysis classInitializationAnalysis) {
    // Only proceed with inlining a static invoke if:
    // - the holder for the target is a subtype of the holder for the method,
    // - the target method always triggers class initialization of its holder before any other side
    //   effect (hence preserving class initialization semantics),
    // - the current method has already triggered the holder for the target method to be
    //   initialized, or
    // - there is no non-trivial class initializer.
    if (appView.appInfo().isSubtype(context.getHolderType(), target.getHolderType())) {
      return true;
    }
    if (target.getDefinition().getOptimizationInfo().triggersClassInitBeforeAnySideEffect()) {
      return true;
    }
    if (!context.getDefinition().isStatic()) {
      boolean targetIsGuaranteedToBeInitialized =
          appView.withInitializedClassesInInstanceMethods(
              analysis ->
                  analysis.isClassDefinitelyLoadedInInstanceMethod(target.getHolder(), context),
              false);
      if (targetIsGuaranteedToBeInitialized) {
        return true;
      }
    }
    if (classInitializationAnalysis.isClassDefinitelyLoadedBeforeInstruction(
        target.getHolderType(), invoke)) {
      return true;
    }
    // Check for class initializer side effects when loading this class, as inlining might remove
    // the load operation.
    //
    // See https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-5.html#jvms-5.5.
    //
    // For simplicity, we are conservative and consider all interfaces, not only the ones with
    // default methods.
    if (!target.getHolder().classInitializationMayHaveSideEffects(appView)) {
      return true;
    }

    if (appView.rootSet().bypassClinitForInlining.contains(target.getReference())) {
      return true;
    }

    return false;
  }

  @Override
  public void ensureMethodProcessed(
      ProgramMethod target, IRCode inlinee, OptimizationFeedback feedback) {
    if (!target.getDefinition().isProcessed()) {
      if (Log.ENABLED) {
        Log.verbose(getClass(), "Forcing extra inline on " + target.toSourceString());
      }
      inliner.performInlining(target, inlinee, feedback, methodProcessor, Timing.empty());
    }
  }

  @Override
  public boolean allowInliningOfInvokeInInlinee(
      InlineAction action,
      int inliningDepth,
      WhyAreYouNotInliningReporter whyAreYouNotInliningReporter) {
    assert inliningDepth > 0;

    if (action.reason.mustBeInlined()) {
      return true;
    }

    int threshold = appView.options().applyInliningToInlineeMaxDepth;
    if (inliningDepth <= threshold) {
      return true;
    }

    whyAreYouNotInliningReporter.reportWillExceedMaxInliningDepth(inliningDepth, threshold);
    return false;
  }

  @Override
  public boolean canInlineInstanceInitializer(
      IRCode inlinee, WhyAreYouNotInliningReporter whyAreYouNotInliningReporter) {
    // In the Java VM Specification section "4.10.2.4. Instance Initialization Methods and
    // Newly Created Objects" it says:
    //
    // Before that method invokes another instance initialization method of myClass or its direct
    // superclass on this, the only operation the method can perform on this is assigning fields
    // declared within myClass.

    // Allow inlining a constructor into a constructor of the same class, as the constructor code
    // is expected to adhere to the VM specification.
    DexType callerMethodHolder = method.getHolderType();
    DexType calleeMethodHolder = inlinee.method().holder();
    // Calling a constructor on the same class from a constructor can always be inlined.
    if (method.getDefinition().isInstanceInitializer()
        && callerMethodHolder == calleeMethodHolder) {
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
        DexEncodedField target = appView.appInfo().lookupInstanceTarget(field);
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
      IRCode code,
      InvokeMethod invoke,
      InlineeWithReason inlinee,
      BasicBlock block,
      WhyAreYouNotInliningReporter whyAreYouNotInliningReporter) {
    if (inlinee.reason.mustBeInlined()) {
      return false;
    }
    return willExceedInstructionBudget(inlinee, whyAreYouNotInliningReporter)
        || willExceedMonitorEnterValuesBudget(code, invoke, inlinee, whyAreYouNotInliningReporter)
        || willExceedControlFlowResolutionBlocksBudget(
            inlinee, block, whyAreYouNotInliningReporter);
  }

  private boolean willExceedInstructionBudget(
      InlineeWithReason inlinee, WhyAreYouNotInliningReporter whyAreYouNotInliningReporter) {
    int numberOfInstructions = Inliner.numberOfInstructions(inlinee.code);
    if (instructionAllowance < Inliner.numberOfInstructions(inlinee.code)) {
      whyAreYouNotInliningReporter.reportWillExceedInstructionBudget(
          numberOfInstructions, instructionAllowance);
      return true;
    }
    return false;
  }

  /**
   * If inlining would lead to additional lock values in the caller, then check that the number of
   * lock values after inlining would not exceed the threshold.
   *
   * <p>The motivation for limiting the number of locks in a given method is that the register
   * allocator will attempt to pin a register for each lock value. Thus, if a method has many locks,
   * many registers will be pinned, which will lead to high register pressure.
   */
  private boolean willExceedMonitorEnterValuesBudget(
      IRCode code,
      InvokeMethod invoke,
      InlineeWithReason inlinee,
      WhyAreYouNotInliningReporter whyAreYouNotInliningReporter) {
    if (!code.metadata().mayHaveMonitorInstruction()) {
      return false;
    }

    if (!inlinee.code.metadata().mayHaveMonitorInstruction()) {
      return false;
    }

    Set<DexType> constantMonitorEnterValues = Sets.newIdentityHashSet();
    Set<Value> nonConstantMonitorEnterValues = Sets.newIdentityHashSet();
    collectAllMonitorEnterValues(code, constantMonitorEnterValues, nonConstantMonitorEnterValues);
    if (constantMonitorEnterValues.isEmpty() && nonConstantMonitorEnterValues.isEmpty()) {
      return false;
    }

    for (Monitor monitor : inlinee.code.<Monitor>instructions(Instruction::isMonitorEnter)) {
      Value monitorEnterValue = monitor.object().getAliasedValue();
      if (monitorEnterValue.isDefinedByInstructionSatisfying(Instruction::isArgument)) {
        monitorEnterValue =
            invoke
                .arguments()
                .get(monitorEnterValue.definition.asArgument().getIndex())
                .getAliasedValue();
      }
      addMonitorEnterValue(
          monitorEnterValue, constantMonitorEnterValues, nonConstantMonitorEnterValues);
    }

    int numberOfMonitorEnterValuesAfterInlining =
        constantMonitorEnterValues.size() + nonConstantMonitorEnterValues.size();
    int threshold = appView.options().inliningMonitorEnterValuesAllowance;
    if (numberOfMonitorEnterValuesAfterInlining > threshold) {
      whyAreYouNotInliningReporter.reportWillExceedMonitorEnterValuesBudget(
          numberOfMonitorEnterValuesAfterInlining, threshold);
      return true;
    }

    return false;
  }

  /**
   * Inlining could lead to an explosion of move-exception and resolution moves. As an example,
   * consider the following piece of code.
   *
   * <pre>
   *   try {
   *     ...
   *     foo();
   *     ...
   *   } catch (A e) { ... }
   *   } catch (B e) { ... }
   *   } catch (C e) { ... }
   * </pre>
   *
   * <p>The generated code for the above example will have a move-exception instruction for each of
   * the three catch handlers. Furthermore, the blocks with these move-exception instructions may
   * require a number of resolution moves to setup the register state for the catch handlers. When
   * inlining foo(), the generated code will have a move-exception instruction *for each of the
   * instructions in foo() that can throw*, along with the necessary resolution moves for each
   * exception-edge. We therefore abort inlining if the number of exception-edges explode.
   */
  private boolean willExceedControlFlowResolutionBlocksBudget(
      InlineeWithReason inlinee,
      BasicBlock block,
      WhyAreYouNotInliningReporter whyAreYouNotInliningReporter) {
    if (!block.hasCatchHandlers()) {
      return false;
    }
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
      whyAreYouNotInliningReporter.reportPotentialExplosionInExceptionalControlFlowResolutionBlocks(
          estimatedNumberOfControlFlowResolutionBlocks, threshold);
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
  public DexType getReceiverTypeIfKnown(InvokeMethod invoke) {
    return null; // Maybe improve later.
  }
}
