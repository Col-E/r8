// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.graph.AccessFlags;
import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.ir.analysis.type.TypeEnvironment;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.Invoke;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.code.ValueNumberGenerator;
import com.android.tools.r8.ir.conversion.CallSiteInformation;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.conversion.LensCodeRewriter;
import com.android.tools.r8.ir.conversion.OptimizationFeedback;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class Inliner {
  private static final int INITIAL_INLINING_INSTRUCTION_ALLOWANCE = 1500;

  protected final AppInfoWithLiveness appInfo;
  private final GraphLense graphLense;
  final InternalOptions options;

  // State for inlining methods which are known to be called twice.
  private boolean applyDoubleInlining = false;
  private final Set<DexEncodedMethod> doubleInlineCallers = Sets.newIdentityHashSet();
  private final Set<DexEncodedMethod> doubleInlineSelectedTargets = Sets.newIdentityHashSet();
  private final Map<DexEncodedMethod, DexEncodedMethod> doubleInlineeCandidates = new HashMap<>();

  private final Set<DexMethod> blackList = Sets.newIdentityHashSet();

  public Inliner(
      AppInfoWithLiveness appInfo,
      GraphLense graphLense,
      InternalOptions options) {
    this.appInfo = appInfo;
    this.graphLense = graphLense;
    this.options = options;
    fillInBlackList(appInfo);
  }

  private void fillInBlackList(AppInfoWithLiveness appInfo) {
    blackList.add(appInfo.dexItemFactory.kotlin.intrinsics.throwParameterIsNullException);
    blackList.add(appInfo.dexItemFactory.kotlin.intrinsics.throwNpe);
  }

  public boolean isBlackListed(DexEncodedMethod method) {
    return blackList.contains(method.method) || appInfo.neverInline.contains(method);
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
    ConstraintWithTarget result = ConstraintWithTarget.ALWAYS;
    InliningConstraints inliningConstraints = new InliningConstraints(appInfo);
    InstructionIterator it = code.instructionIterator();
    while (it.hasNext()) {
      Instruction instruction = it.next();
      ConstraintWithTarget state =
          instructionAllowedForInlining(instruction, inliningConstraints, method.method.holder);
      if (state == ConstraintWithTarget.NEVER) {
        result = state;
        break;
      }
      // TODO(b/111080693): we may need to collect all meaningful constraints.
      result = ConstraintWithTarget.meet(result, state, appInfo);
    }
    return result;
  }

  boolean hasInliningAccess(DexEncodedMethod method, DexEncodedMethod target) {
    if (!isVisibleWithFlags(target.method.holder, method.method.holder, target.accessFlags)) {
      return false;
    }
    // The class needs also to be visible for us to have access.
    DexClass targetClass = appInfo.definitionFor(target.method.holder);
    return isVisibleWithFlags(target.method.holder, method.method.holder, targetClass.accessFlags);
  }

  private boolean isVisibleWithFlags(DexType target, DexType context, AccessFlags flags) {
    if (flags.isPublic()) {
      return true;
    }
    if (flags.isPrivate()) {
      return target == context;
    }
    if (flags.isProtected()) {
      return context.isSubtypeOf(target, appInfo) || target.isSamePackage(context);
    }
    // package-private
    return target.isSamePackage(context);
  }

  synchronized boolean isDoubleInliningTarget(
      CallSiteInformation callSiteInformation, DexEncodedMethod candidate) {
    return callSiteInformation.hasDoubleCallSite(candidate)
        || doubleInlineSelectedTargets.contains(candidate);
  }

  synchronized DexEncodedMethod doubleInlining(DexEncodedMethod method,
      DexEncodedMethod target) {
    if (!applyDoubleInlining) {
      if (doubleInlineeCandidates.containsKey(target)) {
        // Both calls can be inlined.
        doubleInlineCallers.add(doubleInlineeCandidates.get(target));
        doubleInlineCallers.add(method);
        doubleInlineSelectedTargets.add(target);
      } else {
        // First call can be inlined.
        doubleInlineeCandidates.put(target, method);
      }
      // Just preparing for double inlining.
      return null;
    } else {
      // Don't perform the actual inlining if this was not selected.
      if (!doubleInlineSelectedTargets.contains(target)) {
        return null;
      }
    }
    return target;
  }

  public synchronized void processDoubleInlineCallers(
      IRConverter converter, OptimizationFeedback feedback) {
    if (doubleInlineCallers.size() > 0) {
      applyDoubleInlining = true;
      List<DexEncodedMethod> methods = doubleInlineCallers
          .stream()
          .sorted(DexEncodedMethod::slowCompare)
          .collect(Collectors.toList());
      for (DexEncodedMethod method : methods) {
        converter.processMethod(method, feedback, x -> false, CallSiteInformation.empty(),
            Outliner::noProcessing);
        assert method.isProcessed();
      }
    }
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
    NEVER(1),     // Never inline this.
    SAMECLASS(2), // Inlineable into methods with same holder.
    PACKAGE(4),   // Inlineable into methods with holders from the same package.
    SUBCLASS(8),  // Inlineable into methods with holders from a subclass in a different package.
    ALWAYS(16);   // No restrictions for inlining this.

    int value;

    Constraint(int value) {
      this.value = value;
    }

    static {
      assert NEVER.ordinal() < SAMECLASS.ordinal();
      assert SAMECLASS.ordinal() < PACKAGE.ordinal();
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
        DexType contextHolder,
        DexType targetHolder,
        AccessFlags flags,
        AppInfoWithSubtyping appInfo) {
      if (flags.isPublic()) {
        return ALWAYS;
      } else if (flags.isPrivate()) {
        return targetHolder == contextHolder
            ? new ConstraintWithTarget(Constraint.SAMECLASS, targetHolder) : NEVER;
      } else if (flags.isProtected()) {
        if (targetHolder.isSamePackage(contextHolder)) {
          // Even though protected, this is visible via the same package from the context.
          return new ConstraintWithTarget(Constraint.PACKAGE, targetHolder);
        } else if (contextHolder.isSubtypeOf(targetHolder, appInfo)) {
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
        DexType context, DexType clazz, AppInfoWithSubtyping appInfo) {
      if (clazz.isArrayType()) {
        return classIsVisible(context, clazz.toArrayElementType(appInfo.dexItemFactory), appInfo);
      }

      if (clazz.isPrimitiveType()) {
        return ALWAYS;
      }

      DexClass definition = appInfo.definitionFor(clazz);
      return definition == null ? NEVER
          : deriveConstraint(context, clazz, definition.accessFlags, appInfo);
    }

    public static ConstraintWithTarget meet(
        ConstraintWithTarget one, ConstraintWithTarget other, AppInfoWithSubtyping appInfo) {
      if (one.equals(other)) {
        return one;
      }
      if (other.constraint.ordinal() < one.constraint.ordinal()) {
        return meet(other, one, appInfo);
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
      // SAMECLASS <= SAMECLASS, PACKAGE, SUBCLASS
      if (Constraint.SAMECLASS.isSet(constraint)) {
        assert one.constraint == Constraint.SAMECLASS;
        if (other.constraint == Constraint.SAMECLASS) {
          assert one.targetHolder != other.targetHolder;
          return NEVER;
        }
        if (other.constraint == Constraint.PACKAGE) {
          if (one.targetHolder.isSamePackage(other.targetHolder)) {
            return one;
          }
          return NEVER;
        }
        assert other.constraint == Constraint.SUBCLASS;
        if (one.targetHolder.isSubtypeOf(other.targetHolder, appInfo)) {
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
        // TODO(b/111080693): towards finer-grained constraints, we need both.
        // The target method is still inlineable to methods with a holder from the same package of
        // one's holder and a subtype of other's holder.
        return NEVER;
      }
      // SUBCLASS <= SUBCLASS
      assert Constraint.SUBCLASS.isSet(constraint);
      assert one.constraint == other.constraint;
      assert one.targetHolder != other.targetHolder;
      if (one.targetHolder.isSubtypeOf(other.targetHolder, appInfo)) {
        return one;
      }
      if (other.targetHolder.isSubtypeOf(one.targetHolder, appInfo)) {
        return other;
      }
      // SUBCLASS of x and SUBCLASS of y while x and y are not a subtype of each other.
      return NEVER;
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
  }

  static public class InlineAction {

    public final DexEncodedMethod target;
    public final Invoke invoke;
    final Reason reason;

    InlineAction(DexEncodedMethod target, Invoke invoke, Reason reason) {
      this.target = target;
      this.invoke = invoke;
      this.reason = reason;
    }

    boolean ignoreInstructionBudget() {
      return reason != Reason.SIMPLE;
    }

    public IRCode buildInliningIR(
        ValueNumberGenerator generator,
        AppInfoWithSubtyping appInfo,
        GraphLense graphLense,
        InternalOptions options,
        Position callerPosition) {
      // Build the IR for a yet not processed method, and perform minimal IR processing.
      Origin origin = appInfo.originFor(target.method.holder);
      IRCode code =
          target.buildInliningIR(appInfo, graphLense, options, generator, callerPosition, origin);
      if (!target.isProcessed()) {
        new LensCodeRewriter(graphLense, appInfo).rewrite(code, target);
      }
      return code;
    }
  }

  final int numberOfInstructions(IRCode code) {
    int numOfInstructions = 0;
    for (BasicBlock block : code.blocks) {
      numOfInstructions += block.getInstructions().size();
    }
    return numOfInstructions;
  }

  boolean legalConstructorInline(DexEncodedMethod method,
      InvokeMethod invoke, IRCode code) {

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
          seenSuperInvoke = appInfo.dexItemFactory.isConstructor(target);
          boolean callOnConstructorThatCallsConstructorSameClass =
              calleeMethodHolder == target.holder;
          boolean callOnSupertypeOfThisInConstructor =
              callerMethodHolder.isImmediateSubtypeOf(target.holder)
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
        DexEncodedField target = appInfo.lookupInstanceTarget(field.getHolder(), field);
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
    performInliningImpl(oracle, oracle, method, code);
  }

  public void performInlining(
      DexEncodedMethod method,
      IRCode code,
      TypeEnvironment typeEnvironment,
      Predicate<DexEncodedMethod> isProcessedConcurrently,
      CallSiteInformation callSiteInformation) {

    DefaultInliningOracle oracle = createDefaultOracle(
        method, code, typeEnvironment,
        isProcessedConcurrently, callSiteInformation,
        options.inliningInstructionLimit,
        INITIAL_INLINING_INSTRUCTION_ALLOWANCE - numberOfInstructions(code));

    performInliningImpl(oracle, oracle, method, code);
  }

  public DefaultInliningOracle createDefaultOracle(
      DexEncodedMethod method,
      IRCode code,
      TypeEnvironment typeEnvironment,
      Predicate<DexEncodedMethod> isProcessedConcurrently,
      CallSiteInformation callSiteInformation,
      int inliningInstructionLimit,
      int inliningInstructionAllowance) {

    return new DefaultInliningOracle(
        this,
        method,
        code,
        typeEnvironment,
        callSiteInformation,
        isProcessedConcurrently,
        options,
        inliningInstructionLimit,
        inliningInstructionAllowance);
  }

  private void performInliningImpl(
      InliningStrategy strategy, InliningOracle oracle, DexEncodedMethod method, IRCode code) {
    if (strategy.exceededAllowance()) {
      return;
    }

    List<BasicBlock> blocksToRemove = new ArrayList<>();
    ListIterator<BasicBlock> blockIterator = code.listIterator();
    while (blockIterator.hasNext() && !strategy.exceededAllowance()) {
      BasicBlock block = blockIterator.next();
      if (blocksToRemove.contains(block)) {
        continue;
      }
      InstructionListIterator iterator = block.listIterator();
      while (iterator.hasNext() && !strategy.exceededAllowance()) {
        Instruction current = iterator.next();
        if (current.isInvokeMethod()) {
          InvokeMethod invoke = current.asInvokeMethod();
          InlineAction result = invoke.computeInlining(oracle, method.method.holder);
          if (result != null) {
            DexEncodedMethod target = result.target;
            Position invokePosition = invoke.getPosition();
            if (invokePosition.method == null) {
              assert invokePosition.isNone();
              invokePosition = Position.noneWithMethod(method.method, null);
            }
            assert invokePosition.callerPosition == null
                || invokePosition.getOutermostCaller().method
                    == graphLense.getOriginalMethodSignature(method.method);

            IRCode inlinee =
                result.buildInliningIR(
                    code.valueNumberGenerator, appInfo, graphLense, options, invokePosition);
            if (inlinee != null) {
              // TODO(64432527): Get rid of this additional check by improved inlining.
              if (block.hasCatchHandlers() && inlinee.computeNormalExitBlocks().isEmpty()) {
                continue;
              }

              // If this code did not go through the full pipeline, apply inlining to make sure
              // that force inline targets get processed.
              strategy.ensureMethodProcessed(target, inlinee);

              // Make sure constructor inlining is legal.
              assert !target.isClassInitializer();
              if (!strategy.isValidTarget(invoke, target, inlinee)) {
                continue;
              }
              DexType downcast = createDowncastIfNeeded(strategy, invoke, target);
              // Inline the inlinee code in place of the invoke instruction
              // Back up before the invoke instruction.
              iterator.previous();
              strategy.markInlined(inlinee);
              if (!strategy.exceededAllowance() || result.ignoreInstructionBudget()) {
                BasicBlock invokeSuccessor =
                    iterator.inlineInvoke(code, inlinee, blockIterator, blocksToRemove, downcast);
                blockIterator = strategy.
                    updateTypeInformationIfNeeded(inlinee, blockIterator, block, invokeSuccessor);

                // If we inlined the invoke from a bridge method, it is no longer a bridge method.
                if (method.accessFlags.isBridge()) {
                  method.accessFlags.unsetSynthetic();
                  method.accessFlags.unsetBridge();
                }

                method.copyMetadataFromInlinee(target);
              }
            }
          }
        }
      }
    }
    oracle.finish();
    code.removeBlocks(blocksToRemove);
    code.removeAllTrivialPhis();
    assert code.isConsistentSSA();
  }

  private DexType createDowncastIfNeeded(
      InliningStrategy strategy, InvokeMethod invoke, DexEncodedMethod target) {
    if (invoke.isInvokeMethodWithReceiver()) {
      // If the invoke has a receiver but the actual type of the receiver is different
      // from the computed target holder, inlining requires a downcast of the receiver.
      DexType assumedReceiverType = strategy.getReceiverTypeIfKnown(invoke);
      if (assumedReceiverType == null) {
        // In case we don't know exact type of the receiver we use declared
        // method holder as a fallback.
        assumedReceiverType = invoke.getInvokedMethod().getHolder();
      }
      if (assumedReceiverType != target.method.getHolder()) {
        return target.method.getHolder();
      }
    }
    return null;
  }
}
