// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.graph.AccessFlags;
import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ClassHierarchy;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.graph.NestMemberClassAttribute;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.CatchHandlers.CatchHandler;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.Invoke;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Throw;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.code.ValueNumberGenerator;
import com.android.tools.r8.ir.conversion.CallSiteInformation;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.conversion.LensCodeRewriter;
import com.android.tools.r8.ir.desugar.TwrCloseResourceRewriter;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackIgnore;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.MainDexClasses;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Predicate;

public class Inliner {

  protected final AppView<AppInfoWithLiveness> appView;
  final MainDexClasses mainDexClasses;

  // State for inlining methods which are known to be called twice.
  private boolean applyDoubleInlining = false;
  private final Set<DexEncodedMethod> doubleInlineCallers = Sets.newIdentityHashSet();
  private final Set<DexEncodedMethod> doubleInlineSelectedTargets = Sets.newIdentityHashSet();
  private final Map<DexEncodedMethod, DexEncodedMethod> doubleInlineeCandidates = new HashMap<>();

  private final Set<DexMethod> blackList = Sets.newIdentityHashSet();
  private final LensCodeRewriter lensCodeRewriter;

  public Inliner(
      AppView<AppInfoWithLiveness> appView,
      MainDexClasses mainDexClasses,
      LensCodeRewriter lensCodeRewriter) {
    this.appView = appView;
    this.mainDexClasses = mainDexClasses;
    this.lensCodeRewriter = lensCodeRewriter;
    fillInBlackList();
  }

  private void fillInBlackList() {
    blackList.add(appView.dexItemFactory().kotlin.intrinsics.throwParameterIsNullException);
    blackList.add(appView.dexItemFactory().kotlin.intrinsics.throwNpe);
  }

  public boolean isBlackListed(DexMethod method) {
    return blackList.contains(appView.graphLense().getOriginalMethodSignature(method))
        || appView.appInfo().isPinned(method)
        || appView.appInfo().neverInline.contains(method)
        || TwrCloseResourceRewriter.isSynthesizedCloseResourceMethod(method, appView);
  }

  private ConstraintWithTarget instructionAllowedForInlining(
      Instruction instruction, InliningConstraints inliningConstraints, DexType invocationContext) {
    ConstraintWithTarget result =
        instruction.inliningConstraint(inliningConstraints, invocationContext);
    if (result == ConstraintWithTarget.NEVER && instruction.isDebugInstruction()) {
      return ConstraintWithTarget.ALWAYS;
    }
    return result;
  }

  public ConstraintWithTarget computeInliningConstraint(IRCode code, DexEncodedMethod method) {
    if (appView.options().canHaveDalvikCatchHandlerVerificationBug()
        && useReflectiveOperationExceptionOrUnknownClassInCatch(code)) {
      return ConstraintWithTarget.NEVER;
    }

    if (appView.options().canHaveDalvikIntUsedAsNonIntPrimitiveTypeBug()
        && returnsIntAsBoolean(code, method)) {
      return ConstraintWithTarget.NEVER;
    }

    ConstraintWithTarget result = ConstraintWithTarget.ALWAYS;
    InliningConstraints inliningConstraints =
        new InliningConstraints(appView, GraphLense.getIdentityLense());
    for (Instruction instruction : code.instructions()) {
      ConstraintWithTarget state =
          instructionAllowedForInlining(instruction, inliningConstraints, method.method.holder);
      if (state == ConstraintWithTarget.NEVER) {
        result = state;
        break;
      }
      // TODO(b/128967328): we may need to collect all meaningful constraints.
      result = ConstraintWithTarget.meet(result, state, appView);
    }
    return result;
  }

  private boolean returnsIntAsBoolean(IRCode code, DexEncodedMethod method) {
    DexType returnType = method.method.proto.returnType;
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

  boolean hasInliningAccess(DexEncodedMethod method, DexEncodedMethod target) {
    if (!isVisibleWithFlags(target.method.holder, method.method.holder, target.accessFlags)) {
      return false;
    }
    // The class needs also to be visible for us to have access.
    DexClass targetClass = appView.definitionFor(target.method.holder);
    return isVisibleWithFlags(target.method.holder, method.method.holder, targetClass.accessFlags);
  }

  private boolean isVisibleWithFlags(DexType target, DexType context, AccessFlags flags) {
    if (flags.isPublic()) {
      return true;
    }
    if (flags.isPrivate()) {
      return NestUtils.sameNest(target, context, appView);
    }
    if (flags.isProtected()) {
      return appView.appInfo().isSubtype(context, target) || target.isSamePackage(context);
    }
    // package-private
    return target.isSamePackage(context);
  }

  synchronized boolean isDoubleInliningTarget(
      CallSiteInformation callSiteInformation, DexEncodedMethod candidate) {
    return callSiteInformation.hasDoubleCallSite(candidate.method)
        || doubleInlineSelectedTargets.contains(candidate);
  }

  synchronized boolean satisfiesRequirementsForDoubleInlining(
      DexEncodedMethod method, DexEncodedMethod target) {
    if (applyDoubleInlining) {
      // Don't perform the actual inlining if this was not selected.
      return doubleInlineSelectedTargets.contains(target);
    }

    // Just preparing for double inlining.
    recordDoubleInliningCandidate(method, target);
    return false;
  }

  synchronized void recordDoubleInliningCandidate(
      DexEncodedMethod method, DexEncodedMethod target) {
    if (applyDoubleInlining) {
      return;
    }

    if (doubleInlineeCandidates.containsKey(target)) {
      // Both calls can be inlined.
      doubleInlineCallers.add(doubleInlineeCandidates.get(target));
      doubleInlineCallers.add(method);
      doubleInlineSelectedTargets.add(target);
    } else {
      // First call can be inlined.
      doubleInlineeCandidates.put(target, method);
    }
  }

  public void processDoubleInlineCallers(
      IRConverter converter, ExecutorService executorService, OptimizationFeedback feedback)
      throws ExecutionException {
    if (doubleInlineCallers.isEmpty()) {
      return;
    }
    applyDoubleInlining = true;
    List<Future<?>> futures = new ArrayList<>();
    for (DexEncodedMethod method : doubleInlineCallers) {
      futures.add(
          executorService.submit(
              () -> {
                converter.processMethod(
                    method,
                    feedback,
                    doubleInlineCallers::contains,
                    CallSiteInformation.empty(),
                    Outliner::noProcessing);
                assert method.isProcessed();
                return null;
              }));
    }
    ThreadUtils.awaitFutures(futures);
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
        DexType contextHolder, DexType targetHolder, AccessFlags flags, AppView<?> appView) {
      if (flags.isPublic()) {
        return ALWAYS;
      } else if (flags.isPrivate()) {
        DexClass contextHolderClass = appView.definitionFor(contextHolder);
        assert contextHolderClass != null;
        if (contextHolderClass.isInANest()) {
          return NestUtils.sameNest(contextHolder, targetHolder, appView)
              ? new ConstraintWithTarget(Constraint.SAMENEST, targetHolder)
              : NEVER;
        }
        return targetHolder == contextHolder
            ? new ConstraintWithTarget(Constraint.SAMECLASS, targetHolder) : NEVER;
      } else if (flags.isProtected()) {
        if (targetHolder.isSamePackage(contextHolder)) {
          // Even though protected, this is visible via the same package from the context.
          return new ConstraintWithTarget(Constraint.PACKAGE, targetHolder);
        } else if (appView.isSubtype(contextHolder, targetHolder).isTrue()) {
          return new ConstraintWithTarget(Constraint.SUBCLASS, targetHolder);
        }
        return NEVER;
      } else {
        /* package-private */
        return targetHolder.isSamePackage(contextHolder)
            ? new ConstraintWithTarget(Constraint.PACKAGE, targetHolder) : NEVER;
      }
    }

    public static ConstraintWithTarget classIsVisible(
        DexType context, DexType clazz, AppView<?> appView) {
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
    SIMPLE;        // Inlinee has simple code suitable for inlining.

    public boolean mustBeInlined() {
      // TODO(118734615): Include SINGLE_CALLER and DUAL_CALLER here as well?
      return this == FORCE || this == ALWAYS;
    }
  }

  static public class InlineAction {

    public final DexEncodedMethod target;
    public final Invoke invoke;
    final Reason reason;

    private boolean shouldSynthesizeNullCheckForReceiver;

    InlineAction(DexEncodedMethod target, Invoke invoke, Reason reason) {
      this.target = target;
      this.invoke = invoke;
      this.reason = reason;
    }

    void setShouldSynthesizeNullCheckForReceiver() {
      shouldSynthesizeNullCheckForReceiver = true;
    }

    public InlineeWithReason buildInliningIR(
        DexEncodedMethod context,
        ValueNumberGenerator generator,
        AppView<? extends AppInfoWithSubtyping> appView,
        Position callerPosition,
        LensCodeRewriter lensCodeRewriter) {
      Origin origin = appView.appInfo().originFor(target.method.holder);

      // Build the IR for a yet not processed method, and perform minimal IR processing.
      IRCode code = target.buildInliningIR(context, appView, generator, callerPosition, origin);

      // Insert a null check if this is needed to preserve the implicit null check for the
      // receiver.
      if (shouldSynthesizeNullCheckForReceiver) {
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

          // Link the entry block to the successor of the newly inserted block.
          BasicBlock continuationBlock = throwBlock.unlinkSingleSuccessor();
          entryBlock.link(continuationBlock);

          // Replace the last instruction of the entry block, which is now a goto instruction,
          // with an `if-eqz` instruction that jumps to the newly inserted block if the receiver
          // is null.
          If ifInstruction = new If(If.Type.EQ, receiver);
          entryBlock.replaceLastInstruction(ifInstruction, code);
          assert ifInstruction.getTrueTarget() == throwBlock;
          assert ifInstruction.fallthroughBlock() == continuationBlock;

          // Replace the single goto instruction in the newly inserted block by `throw null`.
          InstructionListIterator iterator = throwBlock.listIterator(code);
          Value nullValue = iterator.insertConstNullInstruction(code, appView.options());
          iterator.next();
          iterator.replaceCurrentInstruction(new Throw(nullValue));
        } else {
          assert false : "Unable to synthesize a null check for the receiver";
        }
      }
      if (!target.isProcessed()) {
        lensCodeRewriter.rewrite(code, target);
      }
      return new InlineeWithReason(code, reason);
    }
  }

  public static class InlineeWithReason {

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

  boolean legalConstructorInline(
      DexEncodedMethod method, InvokeMethod invoke, IRCode code, ClassHierarchy hierarchy) {

    // In the Java VM Specification section "4.10.2.4. Instance Initialization Methods and
    // Newly Created Objects" it says:
    //
    // Before that method invokes another instance initialization method of myClass or its direct
    // superclass on this, the only operation the method can perform on this is assigning fields
    // declared within myClass.

    // Allow inlining a constructor into a constructor of the same class, as the constructor code
    // is expected to adhere to the VM specification.
    DexType callerMethodHolder = method.method.holder;
    boolean callerMethodIsConstructor = method.isInstanceInitializer();
    DexType calleeMethodHolder = invoke.asInvokeMethod().getInvokedMethod().holder;
    // Calling a constructor on the same class from a constructor can always be inlined.
    if (callerMethodIsConstructor && callerMethodHolder == calleeMethodHolder) {
      return true;
    }

    // We cannot invoke <init> on other values than |this| on Dalvik 4.4.4. Compute whether
    // the receiver to the call was the this value at the call-site.
    boolean receiverOfInnerCallIsThisOfOuter = invoke.asInvokeDirect().getReceiver().isThis();

    // Don't allow inlining a constructor into a non-constructor if the first use of the
    // un-initialized object is not an argument of an invoke of <init>.
    // Also, we cannot inline a constructor if it initializes final fields, as such is only allowed
    // from within a constructor of the corresponding class.
    // Lastly, we can only inline a constructor, if its own <init> call is on the method's class. If
    // we inline into a constructor, calls to super.<init> are also OK if the receiver of the
    // super.<init> call is the this argument.
    InstructionIterator iterator = code.instructionIterator();
    Instruction instruction = iterator.next();
    // A constructor always has the un-initialized object as the first argument.
    assert instruction.isArgument();
    Value unInitializedObject = instruction.outValue();
    boolean seenSuperInvoke = false;
    while (iterator.hasNext()) {
      instruction = iterator.next();
      if (instruction.inValues().contains(unInitializedObject)) {
        if (instruction.isInvokeDirect() && !seenSuperInvoke) {
          DexMethod target = instruction.asInvokeDirect().getInvokedMethod();
          seenSuperInvoke = appView.dexItemFactory().isConstructor(target);
          boolean callOnConstructorThatCallsConstructorSameClass =
              calleeMethodHolder == target.holder;
          boolean callOnSupertypeOfThisInConstructor =
              hierarchy.isDirectSubtype(callerMethodHolder, target.holder)
                  && instruction.asInvokeDirect().getReceiver() == unInitializedObject
                  && receiverOfInnerCallIsThisOfOuter
                  && callerMethodIsConstructor;
          if (seenSuperInvoke
              // Calls to init on same class than the called constructor are OK.
              && !callOnConstructorThatCallsConstructorSameClass
              // If we are inlining into a constructor, calls to superclass init are only OK on the
              // |this| value in the outer context.
              && !callOnSupertypeOfThisInConstructor) {
            return false;
          }
        }
        if (!seenSuperInvoke) {
          return false;
        }
      }
      if (instruction.isInstancePut()) {
        // Fields may not be initialized outside of a constructor.
        if (!callerMethodIsConstructor) {
          return false;
        }
        DexField field = instruction.asInstancePut().getField();
        DexEncodedField target = appView.appInfo().lookupInstanceTarget(field.holder, field);
        if (target != null && target.accessFlags.isFinal()) {
          return false;
        }
      }
    }
    return true;
  }

  public static class InliningInfo {
    public final DexEncodedMethod target;
    public final DexType receiverType; // null, if unknown

    public InliningInfo(DexEncodedMethod target, DexType receiverType) {
      this.target = target;
      this.receiverType = receiverType;
    }
  }

  public void performForcedInlining(
      DexEncodedMethod method,
      IRCode code,
      Map<InvokeMethod, InliningInfo> invokesToInline) {

    ForcedInliningOracle oracle = new ForcedInliningOracle(method, invokesToInline);
    performInliningImpl(oracle, oracle, method, code, OptimizationFeedbackIgnore.getInstance());
  }

  public void performInlining(
      DexEncodedMethod method,
      IRCode code,
      OptimizationFeedback feedback,
      Predicate<DexEncodedMethod> isProcessedConcurrently,
      CallSiteInformation callSiteInformation) {
    InternalOptions options = appView.options();
    DefaultInliningOracle oracle =
        createDefaultOracle(
            method,
            code,
            isProcessedConcurrently,
            callSiteInformation,
            options.inliningInstructionLimit,
            options.inliningInstructionAllowance - numberOfInstructions(code));
    performInliningImpl(oracle, oracle, method, code, feedback);
  }

  public DefaultInliningOracle createDefaultOracle(
      DexEncodedMethod method,
      IRCode code,
      Predicate<DexEncodedMethod> isProcessedConcurrently,
      CallSiteInformation callSiteInformation,
      int inliningInstructionLimit,
      int inliningInstructionAllowance) {
    return new DefaultInliningOracle(
        appView,
        this,
        method,
        code,
        callSiteInformation,
        isProcessedConcurrently,
        inliningInstructionLimit,
        inliningInstructionAllowance);
  }

  private void performInliningImpl(
      InliningStrategy strategy,
      InliningOracle oracle,
      DexEncodedMethod context,
      IRCode code,
      OptimizationFeedback feedback) {
    AssumeDynamicTypeRemover assumeDynamicTypeRemover = new AssumeDynamicTypeRemover(appView, code);
    Set<BasicBlock> blocksToRemove = Sets.newIdentityHashSet();
    ListIterator<BasicBlock> blockIterator = code.listIterator();
    ClassInitializationAnalysis classInitializationAnalysis =
        new ClassInitializationAnalysis(appView, code);
    while (blockIterator.hasNext()) {
      BasicBlock block = blockIterator.next();
      if (blocksToRemove.contains(block)) {
        continue;
      }
      InstructionListIterator iterator = block.listIterator(code);
      while (iterator.hasNext()) {
        Instruction current = iterator.next();
        if (current.isInvokeMethod()) {
          InvokeMethod invoke = current.asInvokeMethod();
          InlineAction result =
              invoke.computeInlining(oracle, context.method, classInitializationAnalysis);
          if (result != null) {
            if (!(strategy.stillHasBudget() || result.reason.mustBeInlined())) {
              continue;
            }
            DexEncodedMethod target = result.target;
            Position invokePosition = invoke.getPosition();
            if (invokePosition.method == null) {
              assert invokePosition.isNone();
              invokePosition = Position.noneWithMethod(context.method, null);
            }
            assert invokePosition.callerPosition == null
                || invokePosition.getOutermostCaller().method
                    == appView.graphLense().getOriginalMethodSignature(context.method);

            InlineeWithReason inlinee =
                result.buildInliningIR(
                    context, code.valueNumberGenerator, appView, invokePosition, lensCodeRewriter);
            if (inlinee != null) {
              if (strategy.willExceedBudget(inlinee, block)) {
                continue;
              }

              // If this code did not go through the full pipeline, apply inlining to make sure
              // that force inline targets get processed.
              strategy.ensureMethodProcessed(target, inlinee.code, feedback);

              // Make sure constructor inlining is legal.
              assert !target.isClassInitializer();
              if (!strategy.isValidTarget(invoke, target, inlinee.code, appView.appInfo())) {
                continue;
              }

              // Mark AssumeDynamicType instruction for the out-value for removal, if any.
              Value outValue = invoke.outValue();
              if (outValue != null) {
                assumeDynamicTypeRemover.markUsersForRemoval(outValue);
              }

              // Inline the inlinee code in place of the invoke instruction
              // Back up before the invoke instruction.
              iterator.previous();
              strategy.markInlined(inlinee);
              iterator.inlineInvoke(
                  appView,
                  code,
                  inlinee.code,
                  blockIterator,
                  blocksToRemove,
                  getDowncastTypeIfNeeded(strategy, invoke, target));

              if (inlinee.reason == Reason.SINGLE_CALLER) {
                feedback.markInlinedIntoSingleCallSite(target);
              }

              classInitializationAnalysis.notifyCodeHasChanged();
              strategy.updateTypeInformationIfNeeded(inlinee.code, blockIterator, block);

              // If we inlined the invoke from a bridge method, it is no longer a bridge method.
              if (context.accessFlags.isBridge()) {
                context.accessFlags.unsetSynthetic();
                context.accessFlags.unsetBridge();
              }

              context.copyMetadata(target);
            }
          }
        } else if (current.isAssumeDynamicType()) {
          assumeDynamicTypeRemover.removeIfMarked(current.asAssumeDynamicType(), iterator);
        }
      }
    }
    assumeDynamicTypeRemover.removeMarkedInstructions(blocksToRemove);
    assumeDynamicTypeRemover.finish();
    classInitializationAnalysis.finish();
    oracle.finish();
    code.removeBlocks(blocksToRemove);
    code.removeAllTrivialPhis();
    assert code.isConsistentSSA();
  }

  private boolean useReflectiveOperationExceptionOrUnknownClassInCatch(IRCode code) {
    for (BasicBlock block : code.blocks) {
      for (CatchHandler<BasicBlock> catchHandler : block.getCatchHandlers()) {
        if (catchHandler.guard == appView.dexItemFactory().reflectiveOperationExceptionType) {
          return true;
        }
        if (appView.definitionFor(catchHandler.guard) == null) {
          return true;
        }
      }
    }
    return false;
  }

  private static DexType getDowncastTypeIfNeeded(
      InliningStrategy strategy, InvokeMethod invoke, DexEncodedMethod target) {
    if (invoke.isInvokeMethodWithReceiver()) {
      // If the invoke has a receiver but the actual type of the receiver is different
      // from the computed target holder, inlining requires a downcast of the receiver.
      DexType assumedReceiverType = strategy.getReceiverTypeIfKnown(invoke);
      if (assumedReceiverType == null) {
        // In case we don't know exact type of the receiver we use declared
        // method holder as a fallback.
        assumedReceiverType = invoke.getInvokedMethod().holder;
      }
      if (assumedReceiverType != target.method.holder) {
        return target.method.holder;
      }
    }
    return null;
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
