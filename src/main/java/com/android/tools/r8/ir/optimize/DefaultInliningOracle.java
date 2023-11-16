// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static com.android.tools.r8.ir.optimize.inliner.InlinerUtils.addMonitorEnterValue;
import static com.android.tools.r8.ir.optimize.inliner.InlinerUtils.collectAllMonitorEnterValues;
import static com.android.tools.r8.utils.AndroidApiLevelUtils.isApiSafeForInlining;

import com.android.tools.r8.dex.code.DexCheckCast;
import com.android.tools.r8.dex.code.DexInvokeStatic;
import com.android.tools.r8.dex.code.DexMoveResult;
import com.android.tools.r8.dex.code.DexMoveResultObject;
import com.android.tools.r8.dex.code.DexMoveResultWide;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.features.FeatureSplitBoundaryOptimizationUtils;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexItemFactory.BoxUnboxPrimitiveMethodRoundtrip;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis;
import com.android.tools.r8.ir.analysis.inlining.SimpleInliningConstraint;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.Argument;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.CheckCast;
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
import com.android.tools.r8.ir.optimize.Inliner.InlineResult;
import com.android.tools.r8.ir.optimize.Inliner.Reason;
import com.android.tools.r8.ir.optimize.Inliner.RetryAction;
import com.android.tools.r8.ir.optimize.inliner.InliningIRProvider;
import com.android.tools.r8.ir.optimize.inliner.InliningReasonStrategy;
import com.android.tools.r8.ir.optimize.inliner.WhyAreYouNotInliningReporter;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.AssumeInfoCollection;
import com.android.tools.r8.shaking.MainDexInfo;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.InternalOptions.InlinerOptions;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class DefaultInliningOracle implements InliningOracle, InliningStrategy {

  private final AppView<AppInfoWithLiveness> appView;
  private final InternalOptions options;
  private final InlinerOptions inlinerOptions;
  private final MainDexInfo mainDexInfo;
  private final ProgramMethod method;
  private final MethodProcessor methodProcessor;
  private final InliningReasonStrategy reasonStrategy;
  private int instructionAllowance;

  DefaultInliningOracle(
      AppView<AppInfoWithLiveness> appView,
      InliningReasonStrategy inliningReasonStrategy,
      ProgramMethod method,
      MethodProcessor methodProcessor,
      int inliningInstructionAllowance) {
    this.appView = appView;
    this.options = appView.options();
    this.inlinerOptions = options.inlinerOptions();
    this.reasonStrategy = inliningReasonStrategy;
    this.mainDexInfo = appView.appInfo().getMainDexInfo();
    this.method = method;
    this.methodProcessor = methodProcessor;
    this.instructionAllowance = inliningInstructionAllowance;
  }

  @Override
  public AppView<AppInfoWithLiveness> appView() {
    return appView;
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
      SingleResolutionResult<?> resolutionResult,
      ProgramMethod singleTarget,
      WhyAreYouNotInliningReporter whyAreYouNotInliningReporter) {
    // Do not inline if the inlinee is greater than the api caller level.
    // TODO(b/188498051): We should not force inline lower api method calls.
    if (!isApiSafeForInlining(method, singleTarget, options, whyAreYouNotInliningReporter)) {
      return false;
    }

    // We don't inline into constructors when producing class files since this can mess up
    // the stackmap, see b/136250031
    if (method.getDefinition().isInstanceInitializer() && options.isGeneratingClassFiles()) {
      whyAreYouNotInliningReporter.reportNoInliningIntoConstructorsWhenGeneratingClassFiles();
      return false;
    }

    if (method.isStructurallyEqualTo(singleTarget)) {
      // Cannot handle recursive inlining at this point.
      // Force inlined method should never be recursive.
      assert !singleTarget.getOptimizationInfo().forceInline();
      whyAreYouNotInliningReporter.reportRecursiveMethod();
      return false;
    }

    if (canHaveIssuesWithMonitors(singleTarget, method)) {
      return false;
    }

    // We should never even try to inline something that is processed concurrently. It can lead
    // to non-deterministic behaviour as the inlining IR could be built from either original output
    // or optimized code. Right now this happens for the class class staticizer, as it just
    // processes all relevant methods in parallel with the full optimization pipeline enabled.
    // TODO(sgjesse): Add this assert "assert !isProcessedConcurrently.test(candidate);"
    if (methodProcessor.isProcessedConcurrently(singleTarget)) {
      whyAreYouNotInliningReporter.reportProcessedConcurrently();
      return false;
    }

    if (!FeatureSplitBoundaryOptimizationUtils.isSafeForInlining(method, singleTarget, appView)) {
      whyAreYouNotInliningReporter.reportInliningAcrossFeatureSplit();
      return false;
    }

    // Abort inlining attempt if method -> target access is not right.
    if (resolutionResult.isAccessibleFrom(method, appView).isPossiblyFalse()) {
      whyAreYouNotInliningReporter.reportInaccessible();
      return false;
    }

    // Don't inline code with references beyond root main dex classes into a root main dex class.
    // If we do this it can increase the size of the main dex dependent classes.
    if (mainDexInfo.disallowInliningIntoContext(
        appView, method, singleTarget, appView.getSyntheticItems())) {
      whyAreYouNotInliningReporter.reportInlineeRefersToClassesNotInMainDex();
      return false;
    }
    assert !mainDexInfo.disallowInliningIntoContext(
        appView, method, singleTarget, appView.getSyntheticItems());
    return true;
  }

  private boolean canHaveIssuesWithMonitors(ProgramMethod singleTarget, ProgramMethod context) {
    if (options.canHaveIssueWithInlinedMonitors() && hasMonitorsOrIsSynchronized(singleTarget)) {
      return context.getOptimizationInfo().forceInline() || hasMonitorsOrIsSynchronized(context);
    }
    return false;
  }

  public static boolean hasMonitorsOrIsSynchronized(ProgramMethod method) {
    return method.getAccessFlags().isSynchronized()
        || method.getDefinition().getCode().hasMonitorInstructions();
  }

  public boolean satisfiesRequirementsForSimpleInlining(
      InvokeMethod invoke, ProgramMethod target, Optional<InliningIRProvider> inliningIRProvider) {
    // Code size modified by inlining, so only read for non-concurrent methods.
    boolean deterministic = !methodProcessor.isProcessedConcurrently(target);
    if (deterministic) {
      // Check if the inlinee is sufficiently small to inline.
      Code code = target.getDefinition().getCode();
      int instructionLimit = inlinerOptions.getSimpleInliningInstructionLimit();
      int estimatedMaxIncrement =
          getEstimatedMaxInliningInstructionLimitIncrement(invoke, target, inliningIRProvider);
      int estimatedSizeForInlining =
          code.getEstimatedSizeForInliningIfLessThanOrEquals(
              instructionLimit + estimatedMaxIncrement);
      if (estimatedSizeForInlining < 0) {
        return false;
      }
      if (estimatedSizeForInlining <= instructionLimit) {
        return true;
      }
      int actualIncrement =
          getInliningInstructionLimitIncrement(invoke, target, inliningIRProvider);
      if (estimatedSizeForInlining <= instructionLimit + actualIncrement) {
        return true;
      }
    }
    // Even if the inlinee is big it may become simple after inlining. We therefore check if the
    // inlinee's simple inlining constraint is satisfied by the invoke.
    SimpleInliningConstraint simpleInliningConstraint =
        target.getDefinition().getOptimizationInfo().getSimpleInliningConstraint();
    return simpleInliningConstraint.isSatisfied(invoke);
  }

  private int getInliningInstructionLimitIncrement(
      InvokeMethod invoke, ProgramMethod target, Optional<InliningIRProvider> inliningIRProvider) {
    if (!options.inlinerOptions().enableSimpleInliningInstructionLimitIncrement) {
      return 0;
    }
    return getInliningInstructionLimitIncrementForNonNullParamOrThrow(invoke, target)
        + getInliningInstructionLimitIncrementForPrelude(invoke, target, inliningIRProvider)
        + getInliningInstructionLimitIncrementForReturn(invoke);
  }

  private int getEstimatedMaxInliningInstructionLimitIncrement(
      InvokeMethod invoke, ProgramMethod target, Optional<InliningIRProvider> inliningIRProvider) {
    if (!options.inlinerOptions().enableSimpleInliningInstructionLimitIncrement) {
      return 0;
    }
    return getEstimatedMaxInliningInstructionLimitIncrementForNonNullParamOrThrow(invoke, target)
        + getEstimatedMaxInliningInstructionLimitIncrementForPrelude(
            invoke, target, inliningIRProvider)
        + getEstimatedMaxInliningInstructionLimitIncrementForReturn(invoke);
  }

  private int getInliningInstructionLimitIncrementForNonNullParamOrThrow(
      InvokeMethod invoke, ProgramMethod target) {
    BitSet hints = target.getOptimizationInfo().getNonNullParamOrThrow();
    if (hints == null) {
      return 0;
    }
    int instructionLimit = 0;
    for (int index = invoke.getFirstNonReceiverArgumentIndex();
        index < invoke.arguments().size();
        index++) {
      Value argument = invoke.getArgument(index);
      if (hints.get(index) && argument.getType().isReferenceType() && argument.isNeverNull()) {
        // 5-4 instructions per parameter check are expected to be removed.
        instructionLimit += 4;
      }
    }
    return instructionLimit;
  }

  private int getEstimatedMaxInliningInstructionLimitIncrementForNonNullParamOrThrow(
      InvokeMethod invoke, ProgramMethod target) {
    return getInliningInstructionLimitIncrementForNonNullParamOrThrow(invoke, target);
  }

  private int getInliningInstructionLimitIncrementForPrelude(
      InvokeMethod invoke, ProgramMethod target, Optional<InliningIRProvider> inliningIRProvider) {
    if (inliningIRProvider.isEmpty()
        || invoke.arguments().isEmpty()
        || !target.getDefinition().getCode().isLirCode()) {
      return 0;
    }
    IRCode code = inliningIRProvider.get().getAndCacheInliningIR(invoke, target);
    Iterable<Argument> arguments = code::argumentIterator;
    DexItemFactory factory = appView.dexItemFactory();
    int increment = 0;
    for (Argument argument : arguments) {
      Value argumentValue = argument.outValue();
      for (Instruction user : argumentValue.uniqueUsers()) {
        if (user.isCheckCast()) {
          CheckCast checkCastUser = user.asCheckCast();
          TypeElement argumentType = invoke.getArgument(argument.getIndex()).getType();
          TypeElement castType = checkCastUser.getType().toTypeElement(appView);
          if (argumentType.lessThanOrEqual(castType, appView)) {
            // We can remove the cast inside the inlinee.
            increment += DexCheckCast.SIZE;
          }
        } else {
          DexType argumentType = target.getArgumentType(argument.getIndex());
          BoxUnboxPrimitiveMethodRoundtrip roundtrip =
              factory.getBoxUnboxPrimitiveMethodRoundtrip(argumentType);
          if (roundtrip == null) {
            continue;
          }
          Value invokeArgument = invoke.getArgument(argument.getIndex()).getAliasedValue();
          if (user.isInvokeMethod(roundtrip.getBoxIfPrimitiveElseUnbox())
              && invokeArgument.isDefinedByInstructionSatisfying(
                  definition ->
                      definition.isInvokeMethod(roundtrip.getUnboxIfPrimitiveElseBox()))) {
            // We can remove the unbox/box operation inside the inlinee.
            increment += DexInvokeStatic.SIZE + DexMoveResult.SIZE;
            if (invokeArgument.numberOfAllUsers() == 1 && argumentValue.numberOfAllUsers() == 1) {
              // We can remove the box/unbox operation inside the caller.
              increment += DexInvokeStatic.SIZE + DexMoveResult.SIZE;
            }
          }
        }
      }
    }
    return increment;
  }

  private int getEstimatedMaxInliningInstructionLimitIncrementForPrelude(
      InvokeMethod invoke, ProgramMethod target, Optional<InliningIRProvider> inliningIRProvider) {
    if (inliningIRProvider.isEmpty()
        || invoke.arguments().isEmpty()
        || !target.getDefinition().getCode().isLirCode()) {
      return 0;
    }
    int increment = 0;
    for (int argumentIndex = invoke.getFirstNonReceiverArgumentIndex();
        argumentIndex < invoke.arguments().size();
        argumentIndex++) {
      Value argument = invoke.getArgument(argumentIndex).getAliasedValue();
      if (argument.getType().isReferenceType()) {
        // We can maybe remove a cast inside the inlinee.
        increment += DexCheckCast.SIZE;
      }
      DexType argumentType = target.getArgumentType(argumentIndex);
      BoxUnboxPrimitiveMethodRoundtrip roundtrip =
          appView.dexItemFactory().getBoxUnboxPrimitiveMethodRoundtrip(argumentType);
      if (roundtrip != null
          && argument.isDefinedByInstructionSatisfying(
              definition -> definition.isInvokeMethod(roundtrip.getUnboxIfPrimitiveElseBox()))) {
        // We can maybe remove a unbox/box operation inside the inlinee.
        increment += DexInvokeStatic.SIZE + DexMoveResult.SIZE;
        // We can maybe remove a box/unbox operation inside the caller.
        increment += DexInvokeStatic.SIZE + DexMoveResult.SIZE;
      }
    }
    return increment;
  }

  private int getInliningInstructionLimitIncrementForReturn(InvokeMethod invoke) {
    if (options.isGeneratingDex() && invoke.hasOutValue() && invoke.outValue().hasNonDebugUsers()) {
      assert DexMoveResult.SIZE == DexMoveResultObject.SIZE;
      assert DexMoveResult.SIZE == DexMoveResultWide.SIZE;
      return DexMoveResult.SIZE;
    }
    return 0;
  }

  private int getEstimatedMaxInliningInstructionLimitIncrementForReturn(InvokeMethod invoke) {
    return getInliningInstructionLimitIncrementForReturn(invoke);
  }

  @Override
  public ProgramMethod lookupSingleTarget(InvokeMethod invoke, ProgramMethod context) {
    return invoke.lookupSingleProgramTarget(appView, context);
  }

  @Override
  public InlineResult computeInlining(
      IRCode code,
      InvokeMethod invoke,
      SingleResolutionResult<?> resolutionResult,
      ProgramMethod singleTarget,
      ProgramMethod context,
      ClassInitializationAnalysis classInitializationAnalysis,
      InliningIRProvider inliningIRProvider,
      WhyAreYouNotInliningReporter whyAreYouNotInliningReporter) {
    if (isSingleTargetInvalid(invoke, singleTarget, whyAreYouNotInliningReporter)) {
      return null;
    }

    if (neverInline(invoke, resolutionResult, singleTarget, whyAreYouNotInliningReporter)) {
      if (singleTarget.getDefinition().getOptimizationInfo().forceInline()) {
        throw new Unreachable(
            "Unexpected attempt to force inline method `"
                + singleTarget.toSourceString()
                + "` in `"
                + context.toSourceString()
                + "`.");
      }
      return null;
    }

    Reason reason =
        reasonStrategy.computeInliningReason(
            invoke,
            singleTarget,
            context,
            this,
            inliningIRProvider,
            methodProcessor,
            whyAreYouNotInliningReporter);
    if (reason == Reason.NEVER) {
      return null;
    }

    if (methodProcessor.getCallSiteInformation().hasSingleCallSite(singleTarget, context)
        && !singleTarget.getDefinition().isProcessed()
        && methodProcessor.isPrimaryMethodProcessor()) {
      // The single target has this method as single caller, but the single target is not yet
      // processed. Enqueue the context for processing in the secondary optimization pass to allow
      // the single caller inlining to happen.
      return new RetryAction();
    }

    if (!singleTarget
        .getDefinition()
        .isInliningCandidate(appView, method, whyAreYouNotInliningReporter)) {
      return null;
    }

    if (!passesInliningConstraints(
        resolutionResult,
        singleTarget,
        whyAreYouNotInliningReporter)) {
      return null;
    }

    // Ensure that we don't introduce several monitors in the same method on old device that can
    // choke on this. If a context is forceinline, e.g., from class merging, don't ever inline
    // monitors, since that may conflict with a similar other constructor.
    if (options.canHaveIssueWithInlinedMonitors() && hasMonitorsOrIsSynchronized(singleTarget)) {
      if (context.getOptimizationInfo().forceInline()
          || code.metadata().mayHaveMonitorInstruction()) {
        return null;
      }
    }

    InlineAction.Builder actionBuilder =
        invoke.computeInlining(
            singleTarget, this, classInitializationAnalysis, whyAreYouNotInliningReporter);
    if (actionBuilder == null) {
      return null;
    }

    if (!setDowncastTypeIfNeeded(appView, actionBuilder, invoke, singleTarget, context)) {
      return null;
    }

    // Make sure constructor inlining is legal.
    if (singleTarget.getDefinition().isInstanceInitializer()
        && !canInlineInstanceInitializer(
            code,
            invoke.asInvokeDirect(),
            singleTarget,
            inliningIRProvider,
            whyAreYouNotInliningReporter)) {
      return null;
    }

    if (reason == Reason.MULTI_CALLER_CANDIDATE) {
      assert methodProcessor.isPrimaryMethodProcessor();
      actionBuilder.setReason(
          satisfiesRequirementsForSimpleInlining(
                  invoke, singleTarget, Optional.of(inliningIRProvider))
              ? Reason.SIMPLE
              : reason);
    } else {
      if (reason == Reason.SIMPLE
          && !satisfiesRequirementsForSimpleInlining(
              invoke, singleTarget, Optional.of(inliningIRProvider))) {
        whyAreYouNotInliningReporter.reportInlineeNotSimple();
        return null;
      }
      actionBuilder.setReason(reason);
    }

    return actionBuilder.build();
  }

  private boolean neverInline(
      InvokeMethod invoke,
      SingleResolutionResult<?> resolutionResult,
      ProgramMethod singleTarget,
      WhyAreYouNotInliningReporter whyAreYouNotInliningReporter) {
    DexMethod singleTargetReference = singleTarget.getReference();
    if (!appView.getKeepInfo(singleTarget).isInliningAllowed(options)) {
      whyAreYouNotInliningReporter.reportPinned();
      return true;
    }

    AssumeInfoCollection assumeInfoCollection = appView.getAssumeInfoCollection();
    if (assumeInfoCollection.isSideEffectFree(invoke.getInvokedMethod())
        || assumeInfoCollection.isSideEffectFree(resolutionResult.getResolutionPair())
        || assumeInfoCollection.isSideEffectFree(singleTargetReference)) {
      return !singleTarget.getDefinition().getOptimizationInfo().forceInline();
    }

    if (!appView.testing().allowInliningOfSynthetics
        && appView.getSyntheticItems().isSyntheticClass(singleTarget.getHolder())) {
      return true;
    }

    return false;
  }

  public InlineAction.Builder computeForInvokeWithReceiver(
      InvokeMethodWithReceiver invoke,
      ProgramMethod singleTarget,
      WhyAreYouNotInliningReporter whyAreYouNotInliningReporter) {
    Value receiver = invoke.getReceiver();
    if (receiver.getType().isDefinitelyNull()) {
      // A definitely null receiver will throw an error on call site.
      whyAreYouNotInliningReporter.reportReceiverDefinitelyNull();
      return null;
    }
    if (receiver.getType().isNullable()) {
      assert !receiver.getType().isDefinitelyNull();
      // When inlining an instance method call, we need to preserve the null check for the
      // receiver. Therefore, if the receiver may be null and the candidate inlinee does not
      // throw if the receiver is null before any other side effect, then we must synthesize a
      // null check.
      if (!inlinerOptions.enableInliningOfInvokesWithNullableReceivers) {
        whyAreYouNotInliningReporter.reportReceiverMaybeNull();
        return null;
      }
    }
    return InlineAction.builder().setInvoke(invoke).setTarget(singleTarget);
  }

  public InlineAction.Builder computeForInvokeStatic(
      InvokeStatic invoke,
      ProgramMethod singleTarget,
      ClassInitializationAnalysis classInitializationAnalysis,
      WhyAreYouNotInliningReporter whyAreYouNotInliningReporter) {
    InlineAction.Builder actionBuilder =
        InlineAction.builder().setInvoke(invoke).setTarget(singleTarget);
    if (isTargetClassInitialized(invoke, method, singleTarget, classInitializationAnalysis)) {
      return actionBuilder;
    }
    if (appView.canUseInitClass()
        && inlinerOptions.enableInliningOfInvokesWithClassInitializationSideEffects) {
      return actionBuilder.setShouldEnsureStaticInitialization();
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
    // - the current method has already triggered the holder for the target method to be
    //   initialized, or
    // - there is no non-trivial class initializer.
    if (appView.appInfo().isSubtype(context.getHolderType(), target.getHolderType())) {
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
    if (!target.getHolder().classInitializationMayHaveSideEffectsInContext(appView, context)) {
      return true;
    }

    if (appView.rootSet().bypassClinitForInlining.contains(target.getReference())) {
      return true;
    }

    return false;
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public boolean canInlineInstanceInitializer(
      IRCode code,
      InvokeDirect invoke,
      ProgramMethod singleTarget,
      InliningIRProvider inliningIRProvider,
      WhyAreYouNotInliningReporter whyAreYouNotInliningReporter) {
    if (!inlinerOptions.isConstructorInliningEnabled()) {
      return false;
    }

    IRCode inlinee = inliningIRProvider.getInliningIR(invoke, singleTarget);

    // In the Java VM Specification section "4.10.2.4. Instance Initialization Methods and
    // Newly Created Objects" it says:
    //
    // Before that method invokes another instance initialization method of myClass or its direct
    // superclass on this, the only operation the method can perform on this is assigning fields
    // declared within myClass.

    // Allow inlining a constructor into a constructor of the same class, as the constructor code
    // is expected to adhere to the VM specification.
    DexType callerMethodHolder = method.getHolderType();
    DexType calleeMethodHolder = singleTarget.getHolderType();

    // Forwarding constructor calls that target a constructor in the same class can always be
    // inlined.
    if (method.getDefinition().isInstanceInitializer()
        && callerMethodHolder == calleeMethodHolder
        && invoke.getReceiver() == code.getThis()) {
      inliningIRProvider.cacheInliningIR(invoke, inlinee);
      return true;
    }

    @SuppressWarnings("ReferenceEquality")
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
      if (instruction.isInvokeConstructor(appView.dexItemFactory())) {
        InvokeDirect initCall = instruction.asInvokeDirect();
        Value receiver = initCall.getReceiver().getAliasedValue();
        if (receiver == thisValue) {
          // The <init>() call of the constructor must be on the same class when targeting the JVM
          // and Dalvik.
          if (!options.canInitNewInstanceUsingSuperclassConstructor()
              && calleeMethodHolder != initCall.getInvokedMethod().getHolderType()) {
            whyAreYouNotInliningReporter
                .reportUnsafeConstructorInliningDueToIndirectConstructorCall(initCall);
            return false;
          }
          initCallsOnThis.add(initCall);
        }
      } else if (instruction.isInstancePut()) {
        // Final fields may not be initialized outside of a constructor in the enclosing class.
        InstancePut instancePut = instruction.asInstancePut();
        DexField field = instancePut.getField();
        DexEncodedField target = appView.appInfo().lookupInstanceTarget(field);
        if (target == null || target.isFinal()) {
          whyAreYouNotInliningReporter.reportUnsafeConstructorInliningDueToFinalFieldAssignment(
              instancePut);
          return false;
        }
      }
    }

    if (initCallsOnThis.isEmpty()) {
      // In the unusual case where there is no parent/forwarding constructor call, there must be no
      // instance-put instructions that assign fields on the receiver.
      for (Instruction user : thisValue.uniqueUsers()) {
        if (user.isInstancePut() && user.asInstancePut().object().getAliasedValue() == thisValue) {
          whyAreYouNotInliningReporter.reportUnsafeConstructorInliningDueToUninitializedObjectUse(
              user);
          return false;
        }
      }
    } else {
      // Check that there are no uses of the uninitialized object before it gets initialized.
      int markingColor = inlinee.reserveMarkingColor();
      for (InvokeDirect initCallOnThis : initCallsOnThis) {
        BasicBlock block = initCallOnThis.getBlock();
        for (Instruction instruction : block.instructionsBefore(initCallOnThis)) {
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
        for (BasicBlock predecessor : block.getPredecessors()) {
          inlinee.markTransitivePredecessors(predecessor, markingColor);
        }
      }

      for (BasicBlock block : inlinee.getBlocks()) {
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
    }

    inliningIRProvider.cacheInliningIR(invoke, inlinee);
    return true;
  }

  @Override
  public boolean stillHasBudget(
      InlineAction action, WhyAreYouNotInliningReporter whyAreYouNotInliningReporter) {
    if (action.mustBeInlined()) {
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
      InlineAction action,
      IRCode code,
      IRCode inlinee,
      InvokeMethod invoke,
      BasicBlock block,
      WhyAreYouNotInliningReporter whyAreYouNotInliningReporter) {
    if (action.mustBeInlined()) {
      return false;
    }
    return willExceedInstructionBudget(inlinee, whyAreYouNotInliningReporter)
        || willExceedMonitorEnterValuesBudget(code, invoke, inlinee, whyAreYouNotInliningReporter)
        || willExceedControlFlowResolutionBlocksBudget(
            inlinee, block, whyAreYouNotInliningReporter);
  }

  private boolean willExceedInstructionBudget(
      IRCode inlinee, WhyAreYouNotInliningReporter whyAreYouNotInliningReporter) {
    int numberOfInstructions = Inliner.numberOfInstructions(inlinee);
    if (instructionAllowance < Inliner.numberOfInstructions(inlinee)) {
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
      IRCode inlinee,
      WhyAreYouNotInliningReporter whyAreYouNotInliningReporter) {
    if (!code.metadata().mayHaveMonitorInstruction()) {
      return false;
    }

    if (!inlinee.metadata().mayHaveMonitorInstruction()) {
      return false;
    }

    Set<DexType> constantMonitorEnterValues = Sets.newIdentityHashSet();
    Set<Value> nonConstantMonitorEnterValues = Sets.newIdentityHashSet();
    collectAllMonitorEnterValues(code, constantMonitorEnterValues, nonConstantMonitorEnterValues);
    if (constantMonitorEnterValues.isEmpty() && nonConstantMonitorEnterValues.isEmpty()) {
      return false;
    }

    for (Monitor monitor : inlinee.<Monitor>instructions(Instruction::isMonitorEnter)) {
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
    int threshold = inlinerOptions.inliningMonitorEnterValuesAllowance;
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
      IRCode inlinee, BasicBlock block, WhyAreYouNotInliningReporter whyAreYouNotInliningReporter) {
    if (!block.hasCatchHandlers()) {
      return false;
    }
    int numberOfThrowingInstructionsInInlinee = 0;
    for (BasicBlock inlineeBlock : inlinee.blocks) {
      numberOfThrowingInstructionsInInlinee += inlineeBlock.numberOfThrowingInstructions();
    }
    // Estimate the number of "control flow resolution blocks", where we will insert a
    // move-exception instruction (if needed), along with all the resolution moves that
    // will be needed to setup the register state for the catch handler.
    int estimatedNumberOfControlFlowResolutionBlocks =
        numberOfThrowingInstructionsInInlinee * block.numberOfCatchHandlers();
    // Abort if inlining could lead to an explosion in the number of control flow
    // resolution blocks that setup the register state before the actual catch handler.
    int threshold = inlinerOptions.inliningControlFlowResolutionBlocksThreshold;
    if (estimatedNumberOfControlFlowResolutionBlocks >= threshold) {
      whyAreYouNotInliningReporter.reportPotentialExplosionInExceptionalControlFlowResolutionBlocks(
          estimatedNumberOfControlFlowResolutionBlocks, threshold);
      return true;
    }
    return false;
  }

  @Override
  public void markInlined(IRCode inlinee) {
    // TODO(118734615): All inlining use from the budget - should that only be SIMPLE?
    instructionAllowance -= Inliner.numberOfInstructions(inlinee);
  }

  @Override
  public ClassTypeElement getReceiverTypeOrDefault(
      InvokeMethod invoke, ClassTypeElement defaultValue) {
    return defaultValue;
  }
}
