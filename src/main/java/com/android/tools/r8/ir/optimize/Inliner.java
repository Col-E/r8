// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;
import static com.google.common.base.Predicates.not;

import com.android.tools.r8.androidapi.AvailableApiExceptions;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AccessControl;
import com.android.tools.r8.graph.AccessFlags;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.NestMemberClassAttribute;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.ResolutionResult.SingleResolutionResult;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis;
import com.android.tools.r8.ir.analysis.proto.ProtoInliningReasonStrategy;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeAnalysis;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.CatchHandlers.CatchHandler;
import com.android.tools.r8.ir.code.ConstClass;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InitClass;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.Invoke;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.Monitor;
import com.android.tools.r8.ir.code.MoveException;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Throw;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.CodeOptimization;
import com.android.tools.r8.ir.conversion.LensCodeRewriter;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.PostOptimization;
import com.android.tools.r8.ir.desugar.TwrCloseResourceRewriter;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackIgnore;
import com.android.tools.r8.ir.optimize.inliner.DefaultInliningReasonStrategy;
import com.android.tools.r8.ir.optimize.inliner.InliningIRProvider;
import com.android.tools.r8.ir.optimize.inliner.InliningReasonStrategy;
import com.android.tools.r8.ir.optimize.inliner.NopWhyAreYouNotInliningReporter;
import com.android.tools.r8.ir.optimize.inliner.WhyAreYouNotInliningReporter;
import com.android.tools.r8.ir.optimize.lambda.LambdaMerger;
import com.android.tools.r8.kotlin.Kotlin;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.MainDexTracingResult;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.IteratorUtils;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

public class Inliner implements PostOptimization {

  protected final AppView<AppInfoWithLiveness> appView;
  private final Set<DexMethod> extraNeverInlineMethods;
  private final LambdaMerger lambdaMerger;
  private final LensCodeRewriter lensCodeRewriter;
  final MainDexTracingResult mainDexClasses;

  // State for inlining methods which are known to be called twice.
  private boolean applyDoubleInlining = false;
  private final ProgramMethodSet doubleInlineCallers = ProgramMethodSet.create();
  private final ProgramMethodSet doubleInlineSelectedTargets = ProgramMethodSet.create();
  private final Map<DexEncodedMethod, ProgramMethod> doubleInlineeCandidates =
      new IdentityHashMap<>();

  private final AvailableApiExceptions availableApiExceptions;

  public Inliner(
      AppView<AppInfoWithLiveness> appView,
      MainDexTracingResult mainDexClasses,
      LambdaMerger lambdaMerger,
      LensCodeRewriter lensCodeRewriter) {
    Kotlin.Intrinsics intrinsics = appView.dexItemFactory().kotlin.intrinsics;
    this.appView = appView;
    this.extraNeverInlineMethods =
        appView.options().kotlinOptimizationOptions().disableKotlinSpecificOptimizations
            ? ImmutableSet.of()
            : ImmutableSet.of(intrinsics.throwNpe, intrinsics.throwParameterIsNullException);
    this.lambdaMerger = lambdaMerger;
    this.lensCodeRewriter = lensCodeRewriter;
    this.mainDexClasses = mainDexClasses;
    availableApiExceptions =
        appView.options().canHaveDalvikCatchHandlerVerificationBug()
            ? new AvailableApiExceptions(appView.options())
            : null;
  }

  boolean neverInline(
      InvokeMethod invoke,
      SingleResolutionResult resolutionResult,
      ProgramMethod singleTarget,
      WhyAreYouNotInliningReporter whyAreYouNotInliningReporter) {
    AppInfoWithLiveness appInfo = appView.appInfo();
    DexMethod singleTargetReference = singleTarget.getReference();
    if (singleTarget.getDefinition().getOptimizationInfo().forceInline()
        && appInfo.isNeverInlineMethod(singleTargetReference)) {
      throw new Unreachable();
    }

    if (appInfo.isPinned(singleTargetReference)) {
      whyAreYouNotInliningReporter.reportPinned();
      return true;
    }

    if (extraNeverInlineMethods.contains(
            appView.graphLens().getOriginalMethodSignature(singleTargetReference))
        || TwrCloseResourceRewriter.isSynthesizedCloseResourceMethod(
            singleTargetReference, appView)) {
      whyAreYouNotInliningReporter.reportExtraNeverInline();
      return true;
    }

    if (appInfo.isNeverInlineMethod(singleTargetReference)) {
      whyAreYouNotInliningReporter.reportMarkedAsNeverInline();
      return true;
    }

    if (appInfo.noSideEffects.containsKey(invoke.getInvokedMethod())
        || appInfo.noSideEffects.containsKey(resolutionResult.getResolvedMethod().getReference())
        || appInfo.noSideEffects.containsKey(singleTargetReference)) {
      return true;
    }

    return false;
  }

  boolean isDoubleInliningEnabled() {
    return applyDoubleInlining;
  }

  private ConstraintWithTarget instructionAllowedForInlining(
      Instruction instruction, InliningConstraints inliningConstraints, ProgramMethod context) {
    ConstraintWithTarget result = instruction.inliningConstraint(inliningConstraints, context);
    if (result == ConstraintWithTarget.NEVER && instruction.isDebugInstruction()) {
      return ConstraintWithTarget.ALWAYS;
    }
    return result;
  }

  public ConstraintWithTarget computeInliningConstraint(IRCode code) {
    if (containsPotentialCatchHandlerVerificationError(code)) {
      return ConstraintWithTarget.NEVER;
    }

    ProgramMethod context = code.context();
    if (appView.options().canHaveDalvikIntUsedAsNonIntPrimitiveTypeBug()
        && returnsIntAsBoolean(code, context)) {
      return ConstraintWithTarget.NEVER;
    }

    ConstraintWithTarget result = ConstraintWithTarget.ALWAYS;
    InliningConstraints inliningConstraints =
        new InliningConstraints(appView, GraphLens.getIdentityLens());
    for (Instruction instruction : code.instructions()) {
      ConstraintWithTarget state =
          instructionAllowedForInlining(instruction, inliningConstraints, context);
      if (state == ConstraintWithTarget.NEVER) {
        result = state;
        break;
      }
      // TODO(b/128967328): we may need to collect all meaningful constraints.
      result = ConstraintWithTarget.meet(result, state, appView);
    }
    return result;
  }

  private boolean returnsIntAsBoolean(IRCode code, ProgramMethod method) {
    DexType returnType = method.getDefinition().returnType();
    for (BasicBlock basicBlock : code.blocks) {
      InstructionIterator instructionIterator = basicBlock.iterator();
      while (instructionIterator.hasNext()) {
        Instruction instruction = instructionIterator.nextUntil(Instruction::isReturn);
        if (instruction != null) {
          if (returnType.isBooleanType() && !instruction.inValues().get(0).knownToBeBoolean()) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public synchronized boolean isDoubleInlineSelectedTarget(ProgramMethod method) {
    return doubleInlineSelectedTargets.contains(method);
  }

  synchronized boolean satisfiesRequirementsForDoubleInlining(
      ProgramMethod method, ProgramMethod target) {
    if (applyDoubleInlining) {
      // Don't perform the actual inlining if this was not selected.
      return doubleInlineSelectedTargets.contains(target);
    }

    // Just preparing for double inlining.
    recordDoubleInliningCandidate(method, target);
    return false;
  }

  synchronized void recordDoubleInliningCandidate(ProgramMethod method, ProgramMethod target) {
    if (applyDoubleInlining) {
      return;
    }

    if (doubleInlineeCandidates.containsKey(target.getDefinition())) {
      // Both calls can be inlined.
      ProgramMethod doubleInlineeCandidate = doubleInlineeCandidates.get(target.getDefinition());
      doubleInlineCallers.add(doubleInlineeCandidate);
      doubleInlineCallers.add(method);
      doubleInlineSelectedTargets.add(target);
    } else {
      // First call can be inlined.
      doubleInlineeCandidates.put(target.getDefinition(), method);
    }
  }

  @Override
  public ProgramMethodSet methodsToRevisit() {
    applyDoubleInlining = true;
    return doubleInlineCallers;
  }

  @Override
  public Collection<CodeOptimization> codeOptimizationsForPostProcessing() {
    // Run IRConverter#optimize.
    return null;  // Technically same as return converter.getOptimizationForPostIRProcessing();
  }

  /**
   * Encodes the constraints for inlining a method's instructions into a different context.
   * <p>
   * This only takes the instructions into account and not whether a method should be inlined or
   * what reason for inlining it might have. Also, it does not take the visibility of the method
   * itself into account.
   */
  public enum Constraint {
    // The ordinal values are important so please do not reorder.
    // Each constraint includes all constraints <= to it.
    // For example, SAMENEST with class X means:
    // - the target is in the same nest as X, or
    // - the target has the same class as X (SAMECLASS <= SAMENEST).
    // SUBCLASS with class X means:
    // - the target is a subclass of X in different package, or
    // - the target is in the same package (PACKAGE <= SUBCLASS), or
    // ...
    // - the target is the same class as X (SAMECLASS <= SUBCLASS).
    NEVER(1), // Never inline this.
    SAMECLASS(2), // Inlineable into methods in the same holder.
    SAMENEST(4), // Inlineable into methods with same nest.
    PACKAGE(8), // Inlineable into methods with holders from the same package.
    SUBCLASS(16), // Inlineable into methods with holders from a subclass in a different package.
    ALWAYS(32); // No restrictions for inlining this.

    int value;

    Constraint(int value) {
      this.value = value;
    }

    static {
      assert NEVER.ordinal() < SAMECLASS.ordinal();
      assert SAMECLASS.ordinal() < SAMENEST.ordinal();
      assert SAMENEST.ordinal() < PACKAGE.ordinal();
      assert PACKAGE.ordinal() < SUBCLASS.ordinal();
      assert SUBCLASS.ordinal() < ALWAYS.ordinal();
    }

    public Constraint meet(Constraint otherConstraint) {
      if (this.ordinal() < otherConstraint.ordinal()) {
        return this;
      }
      return otherConstraint;
    }

    boolean isSet(int value) {
      return (this.value & value) != 0;
    }
  }

  /**
   * Encodes the constraints for inlining, along with the target holder.
   * <p>
   * Constraint itself cannot determine whether or not the method can be inlined if instructions in
   * the method have different constraints with different targets. For example,
   *   SUBCLASS of x.A v.s. PACKAGE of y.B
   * Without any target holder information, min of those two Constraints is PACKAGE, meaning that
   * the current method can be inlined to any method whose holder is in package y. This could cause
   * an illegal access error due to protect members in x.A. Because of different target holders,
   * those constraints should not be combined.
   * <p>
   * Instead, a right constraint for inlining constraint for the example above is: a method whose
   * holder is a subclass of x.A _and_ in the same package of y.B can inline this method.
   */
  public static class ConstraintWithTarget {
    public final Constraint constraint;
    // Note that this is not context---where this constraint is encoded.
    // It literally refers to the holder type of the target, which could be:
    // invoked method in invocations, field in field instructions, type of check-cast, etc.
    final DexType targetHolder;

    public static final ConstraintWithTarget NEVER = new ConstraintWithTarget(Constraint.NEVER);
    public static final ConstraintWithTarget ALWAYS = new ConstraintWithTarget(Constraint.ALWAYS);

    private ConstraintWithTarget(Constraint constraint) {
      assert constraint == Constraint.NEVER || constraint == Constraint.ALWAYS;
      this.constraint = constraint;
      this.targetHolder = null;
    }

    ConstraintWithTarget(Constraint constraint, DexType targetHolder) {
      assert constraint != Constraint.NEVER && constraint != Constraint.ALWAYS;
      assert targetHolder != null;
      this.constraint = constraint;
      this.targetHolder = targetHolder;
    }

    @Override
    public int hashCode() {
      if (targetHolder == null) {
        return constraint.ordinal();
      }
      return constraint.ordinal() * targetHolder.computeHashCode();
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof ConstraintWithTarget)) {
        return false;
      }
      ConstraintWithTarget o = (ConstraintWithTarget) other;
      return this.constraint.ordinal() == o.constraint.ordinal()
          && this.targetHolder == o.targetHolder;
    }

    public static ConstraintWithTarget deriveConstraint(
        ProgramMethod context, DexType targetHolder, AccessFlags<?> flags, AppView<?> appView) {
      if (flags.isPublic()) {
        return ALWAYS;
      } else if (flags.isPrivate()) {
        if (context.getHolder().isInANest()) {
          return NestUtils.sameNest(context.getHolderType(), targetHolder, appView)
              ? new ConstraintWithTarget(Constraint.SAMENEST, targetHolder)
              : NEVER;
        }
        return targetHolder == context.getHolderType()
            ? new ConstraintWithTarget(Constraint.SAMECLASS, targetHolder)
            : NEVER;
      } else if (flags.isProtected()) {
        if (targetHolder.isSamePackage(context.getHolderType())) {
          // Even though protected, this is visible via the same package from the context.
          return new ConstraintWithTarget(Constraint.PACKAGE, targetHolder);
        } else if (appView.isSubtype(context.getHolderType(), targetHolder).isTrue()) {
          return new ConstraintWithTarget(Constraint.SUBCLASS, targetHolder);
        }
        return NEVER;
      } else {
        /* package-private */
        return targetHolder.isSamePackage(context.getHolderType())
            ? new ConstraintWithTarget(Constraint.PACKAGE, targetHolder)
            : NEVER;
      }
    }

    public static ConstraintWithTarget classIsVisible(
        ProgramMethod context, DexType clazz, AppView<?> appView) {
      if (clazz.isArrayType()) {
        return classIsVisible(context, clazz.toArrayElementType(appView.dexItemFactory()), appView);
      }

      if (clazz.isPrimitiveType()) {
        return ALWAYS;
      }

      DexClass definition = appView.definitionFor(clazz);
      return definition == null
          ? NEVER
          : deriveConstraint(context, clazz, definition.accessFlags, appView);
    }

    public static ConstraintWithTarget meet(
        ConstraintWithTarget one, ConstraintWithTarget other, AppView<?> appView) {
      if (one.equals(other)) {
        return one;
      }
      if (other.constraint.ordinal() < one.constraint.ordinal()) {
        return meet(other, one, appView);
      }
      // From now on, one.constraint.ordinal() <= other.constraint.ordinal()
      if (one == NEVER) {
        return NEVER;
      }
      if (other == ALWAYS) {
        return one;
      }
      int constraint = one.constraint.value | other.constraint.value;
      assert !Constraint.NEVER.isSet(constraint);
      assert !Constraint.ALWAYS.isSet(constraint);
      // SAMECLASS <= SAMECLASS, SAMENEST, PACKAGE, SUBCLASS
      if (Constraint.SAMECLASS.isSet(constraint)) {
        assert one.constraint == Constraint.SAMECLASS;
        if (other.constraint == Constraint.SAMECLASS) {
          assert one.targetHolder != other.targetHolder;
          return NEVER;
        }
        if (other.constraint == Constraint.SAMENEST) {
          if (NestUtils.sameNest(one.targetHolder, other.targetHolder, appView)) {
            return one;
          }
          return NEVER;
        }
        if (other.constraint == Constraint.PACKAGE) {
          if (one.targetHolder.isSamePackage(other.targetHolder)) {
            return one;
          }
          return NEVER;
        }
        assert other.constraint == Constraint.SUBCLASS;
        if (appView.isSubtype(one.targetHolder, other.targetHolder).isTrue()) {
          return one;
        }
        return NEVER;
      }
      // SAMENEST <= SAMENEST, PACKAGE, SUBCLASS
      if (Constraint.SAMENEST.isSet(constraint)) {
        assert one.constraint == Constraint.SAMENEST;
        if (other.constraint == Constraint.SAMENEST) {
          if (NestUtils.sameNest(one.targetHolder, other.targetHolder, appView)) {
            return one;
          }
          return NEVER;
        }
        assert verifyAllNestInSamePackage(one.targetHolder, appView);
        if (other.constraint == Constraint.PACKAGE) {
          if (one.targetHolder.isSamePackage(other.targetHolder)) {
            return one;
          }
          return NEVER;
        }
        assert other.constraint == Constraint.SUBCLASS;
        if (allNestMembersSubtypeOf(one.targetHolder, other.targetHolder, appView)) {
          // Then, SAMENEST is a more restrictive constraint.
          return one;
        }
        return NEVER;
      }
      // PACKAGE <= PACKAGE, SUBCLASS
      if (Constraint.PACKAGE.isSet(constraint)) {
        assert one.constraint == Constraint.PACKAGE;
        if (other.constraint == Constraint.PACKAGE) {
          assert one.targetHolder != other.targetHolder;
          if (one.targetHolder.isSamePackage(other.targetHolder)) {
            return one;
          }
          // PACKAGE of x and PACKAGE of y cannot be satisfied together.
          return NEVER;
        }
        assert other.constraint == Constraint.SUBCLASS;
        if (other.targetHolder.isSamePackage(one.targetHolder)) {
          // Then, PACKAGE is more restrictive constraint.
          return one;
        }
        if (appView.isSubtype(one.targetHolder, other.targetHolder).isTrue()) {
          return new ConstraintWithTarget(Constraint.SAMECLASS, one.targetHolder);
        }
        // TODO(b/128967328): towards finer-grained constraints, we need both.
        // The target method is still inlineable to methods with a holder from the same package of
        // one's holder and a subtype of other's holder.
        return NEVER;
      }
      // SUBCLASS <= SUBCLASS
      assert Constraint.SUBCLASS.isSet(constraint);
      assert one.constraint == other.constraint;
      assert one.targetHolder != other.targetHolder;
      if (appView.isSubtype(one.targetHolder, other.targetHolder).isTrue()) {
        return one;
      }
      if (appView.isSubtype(other.targetHolder, one.targetHolder).isTrue()) {
        return other;
      }
      // SUBCLASS of x and SUBCLASS of y while x and y are not a subtype of each other.
      return NEVER;
    }

    private static boolean allNestMembersSubtypeOf(
        DexType nestType, DexType superType, AppView<?> appView) {
      DexClass dexClass = appView.definitionFor(nestType);
      if (dexClass == null) {
        assert false;
        return false;
      }
      if (!dexClass.isInANest()) {
        return appView.isSubtype(dexClass.type, superType).isTrue();
      }
      DexClass nestHost =
          dexClass.isNestHost() ? dexClass : appView.definitionFor(dexClass.getNestHost());
      if (nestHost == null) {
        assert false;
        return false;
      }
      for (NestMemberClassAttribute member : nestHost.getNestMembersClassAttributes()) {
        if (!appView.isSubtype(member.getNestMember(), superType).isTrue()) {
          return false;
        }
      }
      return true;
    }

    private static boolean verifyAllNestInSamePackage(DexType type, AppView<?> appView) {
      String descr = type.getPackageDescriptor();
      DexClass dexClass = appView.definitionFor(type);
      assert dexClass != null;
      if (!dexClass.isInANest()) {
        return true;
      }
      DexClass nestHost =
          dexClass.isNestHost() ? dexClass : appView.definitionFor(dexClass.getNestHost());
      assert nestHost != null;
      for (NestMemberClassAttribute member : nestHost.getNestMembersClassAttributes()) {
        assert member.getNestMember().getPackageDescriptor().equals(descr);
      }
      return true;
    }
  }

  /**
   * Encodes the reason why a method should be inlined.
   * <p>
   * This is independent of determining whether a method can be inlined, except for the FORCE state,
   * that will inline a method irrespective of visibility and instruction checks.
   */
  public enum Reason {
    FORCE,         // Inlinee is marked for forced inlining (bridge method or renamed constructor).
    ALWAYS,        // Inlinee is marked for inlining due to alwaysinline directive.
    SINGLE_CALLER, // Inlinee has precisely one caller.
    DUAL_CALLER,   // Inlinee has precisely two callers.
    SIMPLE,        // Inlinee has simple code suitable for inlining.
    NEVER;         // Inlinee must not be inlined.

    public boolean mustBeInlined() {
      // TODO(118734615): Include SINGLE_CALLER and DUAL_CALLER here as well?
      return this == FORCE || this == ALWAYS;
    }
  }

  public static class InlineAction {

    public final ProgramMethod target;
    public final Invoke invoke;
    final Reason reason;

    private boolean shouldSynthesizeInitClass;
    private boolean shouldSynthesizeNullCheckForReceiver;

    InlineAction(ProgramMethod target, Invoke invoke, Reason reason) {
      this.target = target;
      this.invoke = invoke;
      this.reason = reason;
    }

    void setShouldSynthesizeInitClass() {
      assert !shouldSynthesizeNullCheckForReceiver;
      shouldSynthesizeInitClass = true;
    }

    void setShouldSynthesizeNullCheckForReceiver() {
      assert !shouldSynthesizeInitClass;
      shouldSynthesizeNullCheckForReceiver = true;
    }

    InlineeWithReason buildInliningIR(
        AppView<? extends AppInfoWithClassHierarchy> appView,
        InvokeMethod invoke,
        ProgramMethod context,
        InliningIRProvider inliningIRProvider,
        LambdaMerger lambdaMerger,
        LensCodeRewriter lensCodeRewriter) {
      DexItemFactory dexItemFactory = appView.dexItemFactory();
      InternalOptions options = appView.options();

      // Build the IR for a yet not processed method, and perform minimal IR processing.
      IRCode code = inliningIRProvider.getInliningIR(invoke, target);

      // Insert a init class instruction if this is needed to preserve class initialization
      // semantics.
      if (shouldSynthesizeInitClass) {
        synthesizeInitClass(code);
      }

      // Insert a null check if this is needed to preserve the implicit null check for the receiver.
      // This is only needed if we do not also insert a monitor-enter instruction, since that will
      // throw a NPE if the receiver is null.
      //
      // Note: When generating DEX, we synthesize monitor-enter/exit instructions during IR
      // building, and therefore, we do not need to do anything here. Upon writing, we will use the
      // flag "declared synchronized" instead of "synchronized".
      boolean shouldSynthesizeMonitorEnterExit =
          target.getDefinition().isSynchronized() && options.isGeneratingClassFiles();
      boolean isSynthesizingNullCheckForReceiverUsingMonitorEnter =
          shouldSynthesizeMonitorEnterExit && !target.getDefinition().isStatic();
      if (shouldSynthesizeNullCheckForReceiver
          && !isSynthesizingNullCheckForReceiverUsingMonitorEnter) {
        synthesizeNullCheckForReceiver(appView, code);
      }

      // Insert monitor-enter and monitor-exit instructions if the method is synchronized.
      if (shouldSynthesizeMonitorEnterExit) {
        TypeElement throwableType =
            TypeElement.fromDexType(
                dexItemFactory.throwableType, Nullability.definitelyNotNull(), appView);

        code.prepareBlocksForCatchHandlers();

        // Create a block for holding the monitor-exit instruction.
        BasicBlock monitorExitBlock = new BasicBlock();
        monitorExitBlock.setNumber(code.getNextBlockNumber());

        // For each block in the code that may throw, add a catch-all handler targeting the
        // monitor-exit block.
        List<BasicBlock> moveExceptionBlocks = new ArrayList<>();
        for (BasicBlock block : code.blocks) {
          if (!block.canThrow()) {
            continue;
          }
          if (block.hasCatchHandlers()
              && block.getCatchHandlersWithSuccessorIndexes().hasCatchAll(dexItemFactory)) {
            continue;
          }
          BasicBlock moveExceptionBlock =
              BasicBlock.createGotoBlock(
                  code.getNextBlockNumber(), Position.none(), code.metadata(), monitorExitBlock);
          InstructionListIterator moveExceptionBlockIterator =
              moveExceptionBlock.listIterator(code);
          moveExceptionBlockIterator.setInsertionPosition(Position.syntheticNone());
          moveExceptionBlockIterator.add(
              new MoveException(
                  code.createValue(throwableType), dexItemFactory.throwableType, options));
          block.appendCatchHandler(moveExceptionBlock, dexItemFactory.throwableType);
          moveExceptionBlocks.add(moveExceptionBlock);
        }

        InstructionListIterator monitorExitBlockIterator = null;
        if (!moveExceptionBlocks.isEmpty()) {
          // Create a phi for the exception values such that we can rethrow the exception if needed.
          Value exceptionValue;
          if (moveExceptionBlocks.size() == 1) {
            exceptionValue =
                ListUtils.first(moveExceptionBlocks).getInstructions().getFirst().outValue();
          } else {
            Phi phi = code.createPhi(monitorExitBlock, throwableType);
            List<Value> operands =
                ListUtils.map(
                    moveExceptionBlocks, block -> block.getInstructions().getFirst().outValue());
            phi.addOperands(operands);
            exceptionValue = phi;
          }

          monitorExitBlockIterator = monitorExitBlock.listIterator(code);
          monitorExitBlockIterator.setInsertionPosition(Position.syntheticNone());
          monitorExitBlockIterator.add(new Throw(exceptionValue));
          monitorExitBlock.getMutablePredecessors().addAll(moveExceptionBlocks);

          // Insert the newly created blocks.
          code.blocks.addAll(moveExceptionBlocks);
          code.blocks.add(monitorExitBlock);
        }

        // Create a block for holding the monitor-enter instruction. Note that, since this block
        // is created after we attach catch-all handlers to the code, this block will not have any
        // catch handlers.
        BasicBlock entryBlock = code.entryBlock();
        InstructionListIterator entryBlockIterator = entryBlock.listIterator(code);
        entryBlockIterator.nextUntil(not(Instruction::isArgument));
        entryBlockIterator.previous();
        BasicBlock monitorEnterBlock = entryBlockIterator.split(code, 0, null);
        assert !monitorEnterBlock.hasCatchHandlers();

        InstructionListIterator monitorEnterBlockIterator = monitorEnterBlock.listIterator(code);
        monitorEnterBlockIterator.setInsertionPosition(Position.syntheticNone());

        // If this is a static method, then the class object will act as the lock, so we load it
        // using a const-class instruction.
        Value lockValue;
        if (target.getDefinition().isStatic()) {
          lockValue =
              code.createValue(
                  TypeElement.fromDexType(dexItemFactory.objectType, definitelyNotNull(), appView));
          monitorEnterBlockIterator.add(new ConstClass(lockValue, target.getHolderType()));
        } else {
          lockValue = entryBlock.getInstructions().getFirst().asArgument().outValue();
        }

        // Insert the monitor-enter and monitor-exit instructions.
        monitorEnterBlockIterator.add(new Monitor(Monitor.Type.ENTER, lockValue));
        if (monitorExitBlockIterator != null) {
          monitorExitBlockIterator.previous();
          monitorExitBlockIterator.add(new Monitor(Monitor.Type.EXIT, lockValue));
          monitorExitBlock.close(null);
        }

        for (BasicBlock block : code.blocks) {
          if (block.exit().isReturn()) {
            // Since return instructions are not allowed after a throwing instruction in a block
            // with catch handlers, the call to prepareBlocksForCatchHandlers() has already taken
            // care of ensuring that all return blocks have no throwing instructions.
            assert !block.canThrow();

            InstructionListIterator instructionIterator =
                block.listIterator(code, block.getInstructions().size() - 1);
            instructionIterator.setInsertionPosition(Position.syntheticNone());
            instructionIterator.add(new Monitor(Monitor.Type.EXIT, lockValue));
          }
        }
      }

      if (inliningIRProvider.shouldApplyCodeRewritings(target)) {
        assert lensCodeRewriter != null;
        lensCodeRewriter.rewrite(code, target);
      }
      if (lambdaMerger != null) {
        lambdaMerger.rewriteCodeForInlining(target, code, context, inliningIRProvider);
      }
      assert code.isConsistentSSA();
      return new InlineeWithReason(code, reason);
    }

    private void synthesizeInitClass(IRCode code) {
      List<Value> arguments = code.collectArguments();
      BasicBlock entryBlock = code.entryBlock();

      // Insert a new block between the last argument instruction and the first actual instruction
      // of the method.
      BasicBlock initClassBlock =
          entryBlock.listIterator(code, arguments.size()).split(code, 0, null);
      assert !initClassBlock.hasCatchHandlers();

      InstructionListIterator iterator = initClassBlock.listIterator(code);
      iterator.setInsertionPosition(entryBlock.exit().getPosition());
      iterator.add(new InitClass(code.createValue(TypeElement.getInt()), target.getHolderType()));
    }

    private void synthesizeNullCheckForReceiver(AppView<?> appView, IRCode code) {
      List<Value> arguments = code.collectArguments();
      if (!arguments.isEmpty()) {
        Value receiver = arguments.get(0);
        assert receiver.isThis();

        BasicBlock entryBlock = code.entryBlock();

        // Insert a new block between the last argument instruction and the first actual
        // instruction of the method.
        BasicBlock throwBlock =
            entryBlock.listIterator(code, arguments.size()).split(code, 0, null);
        assert !throwBlock.hasCatchHandlers();

        InstructionListIterator iterator = throwBlock.listIterator(code);
        iterator.setInsertionPosition(entryBlock.exit().getPosition());
        if (appView.options().canUseRequireNonNull()) {
          DexMethod requireNonNullMethod = appView.dexItemFactory().objectsMethods.requireNonNull;
          iterator.add(new InvokeStatic(requireNonNullMethod, null, ImmutableList.of(receiver)));
        } else {
          DexMethod getClassMethod = appView.dexItemFactory().objectMembers.getClass;
          iterator.add(new InvokeVirtual(getClassMethod, null, ImmutableList.of(receiver)));
        }
      } else {
        assert false : "Unable to synthesize a null check for the receiver";
      }
    }
  }

  static class InlineeWithReason {

    final Reason reason;
    final IRCode code;

    InlineeWithReason(IRCode code, Reason reason) {
      this.code = code;
      this.reason = reason;
    }
  }

  static int numberOfInstructions(IRCode code) {
    int numberOfInstructions = 0;
    for (BasicBlock block : code.blocks) {
      for (Instruction instruction : block.getInstructions()) {
        assert !instruction.isDebugInstruction();

        // Do not include argument instructions since they do not materialize in the output.
        if (instruction.isArgument()) {
          continue;
        }

        // Do not include assume instructions in the calculation of the inlining budget, since they
        // do not materialize in the output.
        if (instruction.isAssume()) {
          continue;
        }

        // Do not include goto instructions that target a basic block with exactly one predecessor,
        // since these goto instructions will generally not materialize.
        if (instruction.isGoto()) {
          if (instruction.asGoto().getTarget().getPredecessors().size() == 1) {
            continue;
          }
        }

        // Do not include return instructions since they do not materialize once inlined.
        if (instruction.isReturn()) {
          continue;
        }

        ++numberOfInstructions;
      }
    }
    return numberOfInstructions;
  }

  public static class InliningInfo {
    public final ProgramMethod target;
    public final DexType receiverType; // null, if unknown

    public InliningInfo(ProgramMethod target, DexType receiverType) {
      this.target = target;
      this.receiverType = receiverType;
    }
  }

  public void performForcedInlining(
      ProgramMethod method,
      IRCode code,
      Map<? extends InvokeMethod, InliningInfo> invokesToInline,
      InliningIRProvider inliningIRProvider,
      Timing timing) {
    ForcedInliningOracle oracle = new ForcedInliningOracle(appView, method, invokesToInline);
    performInliningImpl(
        oracle,
        oracle,
        method,
        code,
        OptimizationFeedbackIgnore.getInstance(),
        inliningIRProvider,
        timing);
  }

  public void performInlining(
      ProgramMethod method,
      IRCode code,
      OptimizationFeedback feedback,
      MethodProcessor methodProcessor,
      Timing timing) {
    performInlining(
        method,
        code,
        feedback,
        methodProcessor,
        timing,
        createDefaultInliningReasonStrategy(methodProcessor));
  }

  public void performInlining(
      ProgramMethod method,
      IRCode code,
      OptimizationFeedback feedback,
      MethodProcessor methodProcessor,
      Timing timing,
      InliningReasonStrategy inliningReasonStrategy) {
    InternalOptions options = appView.options();
    DefaultInliningOracle oracle =
        createDefaultOracle(
            method,
            methodProcessor,
            options.inliningInstructionLimit,
            options.inliningInstructionAllowance - numberOfInstructions(code),
            inliningReasonStrategy);
    InliningIRProvider inliningIRProvider =
        new InliningIRProvider(appView, method, code, methodProcessor);
    assert inliningIRProvider.verifyIRCacheIsEmpty();
    performInliningImpl(oracle, oracle, method, code, feedback, inliningIRProvider, timing);
  }

  public InliningReasonStrategy createDefaultInliningReasonStrategy(
      MethodProcessor methodProcessor) {
    DefaultInliningReasonStrategy defaultInliningReasonStrategy =
        new DefaultInliningReasonStrategy(appView, methodProcessor.getCallSiteInformation(), this);
    return appView.withGeneratedMessageLiteShrinker(
        ignore -> new ProtoInliningReasonStrategy(appView, defaultInliningReasonStrategy),
        defaultInliningReasonStrategy);
  }

  public DefaultInliningOracle createDefaultOracle(
      ProgramMethod method,
      MethodProcessor methodProcessor,
      int inliningInstructionLimit,
      int inliningInstructionAllowance) {
    return createDefaultOracle(
        method,
        methodProcessor,
        inliningInstructionLimit,
        inliningInstructionAllowance,
        createDefaultInliningReasonStrategy(methodProcessor));
  }

  public DefaultInliningOracle createDefaultOracle(
      ProgramMethod method,
      MethodProcessor methodProcessor,
      int inliningInstructionLimit,
      int inliningInstructionAllowance,
      InliningReasonStrategy inliningReasonStrategy) {
    return new DefaultInliningOracle(
        appView,
        this,
        inliningReasonStrategy,
        method,
        methodProcessor,
        inliningInstructionLimit,
        inliningInstructionAllowance);
  }

  private void performInliningImpl(
      InliningStrategy strategy,
      InliningOracle oracle,
      ProgramMethod context,
      IRCode code,
      OptimizationFeedback feedback,
      InliningIRProvider inliningIRProvider,
      Timing timing) {
    AssumeRemover assumeRemover = new AssumeRemover(appView, code);
    Set<BasicBlock> blocksToRemove = Sets.newIdentityHashSet();
    BasicBlockIterator blockIterator = code.listIterator();
    ClassInitializationAnalysis classInitializationAnalysis =
        new ClassInitializationAnalysis(appView, code);
    Deque<BasicBlock> inlineeStack = new ArrayDeque<>();
    InternalOptions options = appView.options();
    while (blockIterator.hasNext()) {
      BasicBlock block = blockIterator.next();
      if (!inlineeStack.isEmpty() && inlineeStack.peekFirst() == block) {
        inlineeStack.pop();
      }
      if (blocksToRemove.contains(block)) {
        continue;
      }
      InstructionListIterator iterator = block.listIterator(code);
      while (iterator.hasNext()) {
        Instruction current = iterator.next();
        if (current.isInvokeMethod()) {
          InvokeMethod invoke = current.asInvokeMethod();
          // TODO(b/142116551): This should be equivalent to invoke.lookupSingleTarget()!

          SingleResolutionResult resolutionResult =
              appView
                  .appInfo()
                  .resolveMethod(invoke.getInvokedMethod(), invoke.getInterfaceBit())
                  .asSingleResolution();
          if (resolutionResult == null
              || resolutionResult.isAccessibleFrom(context, appView).isPossiblyFalse()) {
            continue;
          }

          // TODO(b/156853206): Should not duplicate resolution.
          ProgramMethod singleTarget = oracle.lookupSingleTarget(invoke, context);
          if (singleTarget == null) {
            WhyAreYouNotInliningReporter.handleInvokeWithUnknownTarget(invoke, appView, context);
            continue;
          }

          DexEncodedMethod singleTargetMethod = singleTarget.getDefinition();
          WhyAreYouNotInliningReporter whyAreYouNotInliningReporter =
              oracle.isForcedInliningOracle()
                  ? NopWhyAreYouNotInliningReporter.getInstance()
                  : WhyAreYouNotInliningReporter.createFor(singleTarget, appView, context);
          InlineAction action =
              oracle.computeInlining(
                  invoke,
                  resolutionResult,
                  singleTarget,
                  context,
                  classInitializationAnalysis,
                  whyAreYouNotInliningReporter);
          if (action == null) {
            assert whyAreYouNotInliningReporter.unsetReasonHasBeenReportedFlag();
            continue;
          }

          DexType downcastTypeOrNull = getDowncastTypeIfNeeded(strategy, invoke, singleTarget);
          if (downcastTypeOrNull != null) {
            DexClass downcastClass = appView.definitionFor(downcastTypeOrNull, context);
            if (downcastClass == null
                || AccessControl.isClassAccessible(downcastClass, context, appView)
                    .isPossiblyFalse()) {
              continue;
            }
          }

          if (!inlineeStack.isEmpty()
              && !strategy.allowInliningOfInvokeInInlinee(
                  action, inlineeStack.size(), whyAreYouNotInliningReporter)) {
            assert whyAreYouNotInliningReporter.unsetReasonHasBeenReportedFlag();
            continue;
          }

          if (!strategy.stillHasBudget(action, whyAreYouNotInliningReporter)) {
            assert whyAreYouNotInliningReporter.unsetReasonHasBeenReportedFlag();
            continue;
          }

          InlineeWithReason inlinee =
              action.buildInliningIR(
                  appView, invoke, context, inliningIRProvider, lambdaMerger, lensCodeRewriter);
          if (strategy.willExceedBudget(
              code, invoke, inlinee, block, whyAreYouNotInliningReporter)) {
            assert whyAreYouNotInliningReporter.unsetReasonHasBeenReportedFlag();
            continue;
          }

          // If this code did not go through the full pipeline, apply inlining to make sure
          // that force inline targets get processed.
          strategy.ensureMethodProcessed(singleTarget, inlinee.code, feedback);

          // Make sure constructor inlining is legal.
          assert !singleTargetMethod.isClassInitializer();
          if (singleTargetMethod.isInstanceInitializer()
              && !strategy.canInlineInstanceInitializer(
                  inlinee.code, whyAreYouNotInliningReporter)) {
            assert whyAreYouNotInliningReporter.unsetReasonHasBeenReportedFlag();
            continue;
          }

          // Mark AssumeDynamicType instruction for the out-value for removal, if any.
          Value outValue = invoke.outValue();
          if (outValue != null) {
            assumeRemover.markAssumeDynamicTypeUsersForRemoval(outValue);
          }

          boolean inlineeMayHaveInvokeMethod = inlinee.code.metadata().mayHaveInvokeMethod();

          // Inline the inlinee code in place of the invoke instruction
          // Back up before the invoke instruction.
          iterator.previous();
          strategy.markInlined(inlinee);
          iterator.inlineInvoke(
              appView, code, inlinee.code, blockIterator, blocksToRemove, downcastTypeOrNull);

          if (inlinee.reason == Reason.SINGLE_CALLER) {
            feedback.markInlinedIntoSingleCallSite(singleTargetMethod);
          }

          classInitializationAnalysis.notifyCodeHasChanged();
          postProcessInlineeBlocks(code, inlinee.code, blockIterator, block, timing);

          // The synthetic and bridge flags are maintained only if the inlinee has also these flags.
          if (context.getDefinition().isBridge() && !inlinee.code.method().isBridge()) {
            context.getDefinition().accessFlags.demoteFromBridge();
          }
          if (context.getDefinition().accessFlags.isSynthetic()
              && !inlinee.code.method().accessFlags.isSynthetic()) {
            context.getDefinition().accessFlags.demoteFromSynthetic();
          }

          context.getDefinition().copyMetadata(singleTargetMethod);

          if (inlineeMayHaveInvokeMethod && options.applyInliningToInlinee) {
            if (inlineeStack.size() + 1 > options.applyInliningToInlineeMaxDepth
                && appView.appInfo().hasNoAlwaysInlineMethods()
                && appView.appInfo().hasNoForceInlineMethods()) {
              continue;
            }
            // Record that we will be inside the inlinee until the next block.
            BasicBlock inlineeEnd = IteratorUtils.peekNext(blockIterator);
            inlineeStack.push(inlineeEnd);
            // Move the cursor back to where the first inlinee block was added.
            IteratorUtils.previousUntil(blockIterator, previous -> previous == block);
            blockIterator.next();
          }
        } else if (current.isAssume()) {
          assumeRemover.removeIfMarked(current.asAssume(), iterator);
        }
      }
    }
    assert inlineeStack.isEmpty();
    assumeRemover.removeMarkedInstructions(blocksToRemove);
    assumeRemover.finish();
    classInitializationAnalysis.finish();
    code.removeBlocks(blocksToRemove);
    code.removeAllDeadAndTrivialPhis();
    assert code.isConsistentSSA();
  }

  private boolean containsPotentialCatchHandlerVerificationError(IRCode code) {
    if (availableApiExceptions == null) {
      assert !appView.options().canHaveDalvikCatchHandlerVerificationBug();
      return false;
    }
    for (BasicBlock block : code.blocks) {
      for (CatchHandler<BasicBlock> catchHandler : block.getCatchHandlers()) {
        DexClass clazz = appView.definitionFor(catchHandler.guard);
        if ((clazz == null || clazz.isLibraryClass())
            && availableApiExceptions.canCauseVerificationError(catchHandler.guard)) {
          return true;
        }
      }
    }
    return false;
  }

  private DexType getDowncastTypeIfNeeded(
      InliningStrategy strategy, InvokeMethod invoke, ProgramMethod target) {
    if (invoke.isInvokeMethodWithReceiver()) {
      // If the invoke has a receiver but the actual type of the receiver is different
      // from the computed target holder, inlining requires a downcast of the receiver.
      DexType receiverType = strategy.getReceiverTypeIfKnown(invoke);
      if (receiverType == null) {
        // In case we don't know exact type of the receiver we use declared
        // method holder as a fallback.
        receiverType = invoke.getInvokedMethod().holder;
      }
      if (!appView.appInfo().isSubtype(receiverType, target.getHolderType())) {
        return target.getHolderType();
      }
    }
    return null;
  }

  /** Applies member rebinding to the inlinee and inserts assume instructions. */
  private void postProcessInlineeBlocks(
      IRCode code,
      IRCode inlinee,
      BasicBlockIterator blockIterator,
      BasicBlock block,
      Timing timing) {
    BasicBlock state = IteratorUtils.peekNext(blockIterator);

    Set<BasicBlock> inlineeBlocks = SetUtils.newIdentityHashSet(inlinee.blocks);

    // Run member value propagation on the inlinee blocks.
    if (appView.options().enableValuePropagation) {
      rewindBlockIteratorToFirstInlineeBlock(blockIterator, block);
      applyMemberValuePropagationToInlinee(code, blockIterator, block, inlineeBlocks);
    }

    // Add non-null IRs only to the inlinee blocks.
    insertAssumeInstructions(code, blockIterator, block, inlineeBlocks, timing);

    // Restore the old state of the iterator.
    rewindBlockIteratorToFirstInlineeBlock(blockIterator, state);
    // TODO(b/72693244): need a test where refined env in inlinee affects the caller.
  }

  private void insertAssumeInstructions(
      IRCode code,
      BasicBlockIterator blockIterator,
      BasicBlock block,
      Set<BasicBlock> inlineeBlocks,
      Timing timing) {
    rewindBlockIteratorToFirstInlineeBlock(blockIterator, block);
    new AssumeInserter(appView)
        .insertAssumeInstructionsInBlocks(code, blockIterator, inlineeBlocks::contains, timing);
    assert !blockIterator.hasNext();
  }

  private void applyMemberValuePropagationToInlinee(
      IRCode code,
      ListIterator<BasicBlock> blockIterator,
      BasicBlock block,
      Set<BasicBlock> inlineeBlocks) {
    assert IteratorUtils.peekNext(blockIterator) == block;
    Set<Value> affectedValues = Sets.newIdentityHashSet();
    new MemberValuePropagation(appView)
        .run(code, blockIterator, affectedValues, inlineeBlocks::contains);
    if (!affectedValues.isEmpty()) {
      new TypeAnalysis(appView).narrowing(affectedValues);
    }
    assert !blockIterator.hasNext();
  }

  private void rewindBlockIteratorToFirstInlineeBlock(
      ListIterator<BasicBlock> blockIterator, BasicBlock firstInlineeBlock) {
    // Move the cursor back to where the first inlinee block was added.
    while (blockIterator.hasPrevious() && blockIterator.previous() != firstInlineeBlock) {
      // Do nothing.
    }
    assert IteratorUtils.peekNext(blockIterator) == firstInlineeBlock;
  }

  public static boolean verifyNoMethodsInlinedDueToSingleCallSite(AppView<?> appView) {
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      for (DexEncodedMethod method : clazz.methods()) {
        assert !method.getOptimizationInfo().hasBeenInlinedIntoSingleCallSite();
      }
    }
    return true;
  }
}
