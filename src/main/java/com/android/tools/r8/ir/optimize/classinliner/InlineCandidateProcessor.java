// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner;

import static com.android.tools.r8.graph.DexEncodedMethod.asProgramMethodOrNull;
import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.errors.InternalCompilerError;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AccessControl;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.LibraryMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.ResolutionResult;
import com.android.tools.r8.graph.ResolutionResult.SingleResolutionResult;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeAnalysis;
import com.android.tools.r8.ir.code.AliasedValueConfiguration;
import com.android.tools.r8.ir.code.AssumeAndCheckCastAliasedValueConfiguration;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.CheckCast;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.InstanceGet;
import com.android.tools.r8.ir.code.InstancePut;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionOrPhi;
import com.android.tools.r8.ir.code.Invoke;
import com.android.tools.r8.ir.code.Invoke.Type;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.optimize.Inliner;
import com.android.tools.r8.ir.optimize.Inliner.InlineAction;
import com.android.tools.r8.ir.optimize.Inliner.InliningInfo;
import com.android.tools.r8.ir.optimize.Inliner.Reason;
import com.android.tools.r8.ir.optimize.InliningOracle;
import com.android.tools.r8.ir.optimize.classinliner.ClassInliner.EligibilityStatus;
import com.android.tools.r8.ir.optimize.info.FieldOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.MethodOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.ParameterUsagesInfo.ParameterUsage;
import com.android.tools.r8.ir.optimize.info.initializer.InstanceInitializerInfo;
import com.android.tools.r8.ir.optimize.inliner.InliningIRProvider;
import com.android.tools.r8.ir.optimize.inliner.NopWhyAreYouNotInliningReporter;
import com.android.tools.r8.kotlin.KotlinClassLevelInfo;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.WorkList;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

final class InlineCandidateProcessor {

  enum AliasKind {
    DEFINITE,
    MAYBE
  }

  private static final ImmutableSet<If.Type> ALLOWED_ZERO_TEST_TYPES =
      ImmutableSet.of(If.Type.EQ, If.Type.NE);
  private static final AliasedValueConfiguration aliasesThroughAssumeAndCheckCasts =
      AssumeAndCheckCastAliasedValueConfiguration.getInstance();

  private final AppView<AppInfoWithLiveness> appView;
  private final DexItemFactory dexItemFactory;
  private final Inliner inliner;
  private final Function<DexProgramClass, EligibilityStatus> isClassEligible;
  private final MethodProcessor methodProcessor;
  private final ProgramMethod method;
  private final Instruction root;

  private Value eligibleInstance;
  private DexProgramClass eligibleClass;

  private final Map<InvokeMethodWithReceiver, InliningInfo> methodCallsOnInstance =
      new IdentityHashMap<>();

  private final ProgramMethodSet indirectMethodCallsOnInstance = ProgramMethodSet.create();
  private final Map<InvokeMethod, InliningInfo> extraMethodCalls
      = new IdentityHashMap<>();
  private final List<Pair<InvokeMethod, Integer>> unusedArguments
      = new ArrayList<>();

  private final Map<InvokeMethod, ProgramMethod> directInlinees = new IdentityHashMap<>();
  private final List<ProgramMethod> indirectInlinees = new ArrayList<>();

  // Sets of values that must/may be an alias of the "root" instance (including the root instance
  // itself).
  private final ClassInlinerReceiverSet receivers;

  InlineCandidateProcessor(
      AppView<AppInfoWithLiveness> appView,
      Inliner inliner,
      Function<DexProgramClass, EligibilityStatus> isClassEligible,
      MethodProcessor methodProcessor,
      ProgramMethod method,
      Instruction root) {
    this.appView = appView;
    this.dexItemFactory = appView.dexItemFactory();
    this.inliner = inliner;
    this.isClassEligible = isClassEligible;
    this.method = method;
    this.root = root;
    this.methodProcessor = methodProcessor;
    this.receivers = new ClassInlinerReceiverSet(root.outValue());
  }

  DexProgramClass getEligibleClass() {
    return eligibleClass;
  }

  Map<InvokeMethod, ProgramMethod> getDirectInlinees() {
    return directInlinees;
  }

  List<ProgramMethod> getIndirectInlinees() {
    return indirectInlinees;
  }

  ClassInlinerReceiverSet getReceivers() {
    return receivers;
  }

  // Checks if the root instruction defines eligible value, i.e. the value
  // exists and we have a definition of its class.
  EligibilityStatus isInstanceEligible() {
    eligibleInstance = root.outValue();
    if (eligibleInstance == null) {
      return EligibilityStatus.NOT_ELIGIBLE;
    }

    if (root.isNewInstance()) {
      eligibleClass = asProgramClassOrNull(appView.definitionFor(root.asNewInstance().clazz));
      if (eligibleClass == null) {
        return EligibilityStatus.NOT_ELIGIBLE;
      }
      if (eligibleClass.classInitializationMayHaveSideEffects(
          appView,
          // Types that are a super type of the current context are guaranteed to be initialized.
          type -> appView.isSubtype(method.getHolderType(), type).isTrue(),
          Sets.newIdentityHashSet())) {
        return EligibilityStatus.NOT_ELIGIBLE;
      }
      return EligibilityStatus.ELIGIBLE;
    }

    assert root.isStaticGet();

    StaticGet staticGet = root.asStaticGet();
    if (staticGet.instructionMayHaveSideEffects(appView, method)) {
      return EligibilityStatus.NOT_ELIGIBLE;
    }
    DexEncodedField field = appView.appInfo().resolveField(staticGet.getField()).getResolvedField();
    FieldOptimizationInfo optimizationInfo = field.getOptimizationInfo();
    ClassTypeElement dynamicLowerBoundType = optimizationInfo.getDynamicLowerBoundType();
    if (dynamicLowerBoundType == null
        || !dynamicLowerBoundType.equals(optimizationInfo.getDynamicUpperBoundType())) {
      return EligibilityStatus.NOT_ELIGIBLE;
    }
    eligibleClass =
        asProgramClassOrNull(appView.definitionFor(dynamicLowerBoundType.getClassType()));
    if (eligibleClass == null) {
      return EligibilityStatus.NOT_ELIGIBLE;
    }
    return EligibilityStatus.ELIGIBLE;
  }

  // Checks if the class is eligible and is properly used. Regarding general class
  // eligibility rules see comment on computeClassEligible(...).
  //
  // In addition to class being eligible this method also checks:
  //   -- for 'new-instance' root:
  //      * class itself does not have static initializer
  //   -- for 'static-get' root:
  //      * class does not have instance fields
  //      * class is final
  //      * class has class initializer marked as TrivialClassInitializer, and
  //        class initializer initializes the field we are reading here.
  EligibilityStatus isClassAndUsageEligible() {
    return isClassEligible.apply(eligibleClass);
  }

  /**
   * Checks if the inlining candidate instance users are eligible, see comment on {@link
   * ClassInliner#processMethodCode}.
   *
   * @return null if all users are eligible, or the first ineligible user.
   */
  InstructionOrPhi areInstanceUsersEligible(Supplier<InliningOracle> defaultOracle) {
    // No Phi users.
    if (eligibleInstance.hasPhiUsers()) {
      return eligibleInstance.firstPhiUser(); // Not eligible.
    }

    Set<Instruction> currentUsers = eligibleInstance.uniqueUsers();
    while (!currentUsers.isEmpty()) {
      Set<Instruction> indirectUsers = Sets.newIdentityHashSet();
      for (Instruction user : currentUsers) {
        if (user.isAssume() || user.isCheckCast()) {
          if (user.isCheckCast()) {
            boolean isCheckCastUnsafe =
                !appView.appInfo().isSubtype(eligibleClass.type, user.asCheckCast().getType());
            if (isCheckCastUnsafe) {
              return user; // Not eligible.
            }
          }
          Value alias = user.outValue();
          if (receivers.isReceiverAlias(alias)) {
            continue; // Already processed.
          }
          if (alias.hasPhiUsers()) {
            return alias.firstPhiUser(); // Not eligible.
          }
          if (!receivers.addReceiverAlias(alias, AliasKind.DEFINITE)) {
            return user; // Not eligible.
          }
          indirectUsers.addAll(alias.uniqueUsers());
          continue;
        }

        if (user.isInstanceGet()) {
          if (root.isStaticGet()) {
            // We don't have a replacement for this field read.
            return user; // Not eligible.
          }
          DexEncodedField field =
              appView
                  .appInfo()
                  .resolveField(user.asFieldInstruction().getField())
                  .getResolvedField();
          if (field == null || field.isStatic()) {
            return user; // Not eligible.
          }
          continue;
        }

        if (user.isInstancePut()) {
          if (root.isStaticGet()) {
            // We can't remove instructions that mutate the singleton instance.
            return user; // Not eligible.
          }
          if (!receivers.addIllegalReceiverAlias(user.asInstancePut().value())) {
            return user; // Not eligible.
          }
          DexEncodedField field =
              appView
                  .appInfo()
                  .resolveField(user.asFieldInstruction().getField())
                  .getResolvedField();
          if (field == null || field.isStatic()) {
            return user; // Not eligible.
          }
          continue;
        }

        if (user.isInvokeMethod()) {
          InvokeMethod invokeMethod = user.asInvokeMethod();
          SingleResolutionResult resolutionResult =
              appView
                  .appInfo()
                  .resolveMethod(invokeMethod.getInvokedMethod(), invokeMethod.getInterfaceBit())
                  .asSingleResolution();
          if (resolutionResult == null
              || resolutionResult.isAccessibleFrom(method, appView).isPossiblyFalse()) {
            return user; // Not eligible.
          }

          // TODO(b/156853206): Avoid duplicating resolution.
          DexClassAndMethod singleTarget = invokeMethod.lookupSingleTarget(appView, method);
          if (singleTarget == null) {
            return user; // Not eligible.
          }

          if (singleTarget.isLibraryMethod()
              && isEligibleLibraryMethodCall(invokeMethod, singleTarget.asLibraryMethod())) {
            continue;
          }

          ProgramMethod singleProgramTarget = singleTarget.asProgramMethod();
          if (!isEligibleSingleTarget(singleProgramTarget)) {
            return user; // Not eligible.
          }

          if (AccessControl.isClassAccessible(singleProgramTarget.getHolder(), method, appView)
              .isPossiblyFalse()) {
            return user; // Not eligible.
          }

          // Eligible constructor call (for new instance roots only).
          if (user.isInvokeDirect()) {
            InvokeDirect invoke = user.asInvokeDirect();
            if (dexItemFactory.isConstructor(invoke.getInvokedMethod())) {
              boolean isCorrespondingConstructorCall =
                  root.isNewInstance()
                      && !invoke.inValues().isEmpty()
                      && root.outValue() == invoke.getReceiver();
              if (isCorrespondingConstructorCall) {
                InliningInfo inliningInfo = isEligibleConstructorCall(invoke, singleProgramTarget);
                if (inliningInfo != null) {
                  methodCallsOnInstance.put(invoke, inliningInfo);
                  continue;
                }
              }
              assert !isExtraMethodCall(invoke);
              return user; // Not eligible.
            }
          }

          // Eligible virtual method call on the instance as a receiver.
          if (user.isInvokeVirtual() || user.isInvokeInterface()) {
            InvokeMethodWithReceiver invoke = user.asInvokeMethodWithReceiver();
            InliningInfo inliningInfo =
                isEligibleDirectVirtualMethodCall(
                    invoke, resolutionResult, singleProgramTarget, indirectUsers, defaultOracle);
            if (inliningInfo != null) {
              methodCallsOnInstance.put(invoke, inliningInfo);
              continue;
            }
          }

          // Eligible usage as an invocation argument.
          if (isExtraMethodCall(invokeMethod)) {
            assert !invokeMethod.isInvokeSuper();
            assert !invokeMethod.isInvokePolymorphic();
            if (isExtraMethodCallEligible(invokeMethod, singleProgramTarget, defaultOracle)) {
              continue;
            }
          }

          return user; // Not eligible.
        }

        // Allow some IF instructions.
        if (user.isIf()) {
          If ifInsn = user.asIf();
          If.Type type = ifInsn.getType();
          if (ifInsn.isZeroTest() && (type == If.Type.EQ || type == If.Type.NE)) {
            // Allow ==/!= null tests, we know that the instance is a non-null value.
            continue;
          }
        }

        return user; // Not eligible.
      }
      currentUsers = indirectUsers;
    }

    return null; // Eligible.
  }

  // Process inlining, includes the following steps:
  //
  //  * remove linked assume instructions if any so that users of the eligible field are up-to-date.
  //  * replace unused instance usages as arguments which are never used
  //  * inline extra methods if any, collect new direct method calls
  //  * inline direct methods if any
  //  * remove superclass initializer call and field reads
  //  * remove field writes
  //  * remove root instruction
  //
  // Returns `true` if at least one method was inlined.
  boolean processInlining(
      IRCode code,
      Set<Value> affectedValues,
      Supplier<InliningOracle> defaultOracle,
      InliningIRProvider inliningIRProvider)
      throws IllegalClassInlinerStateException {
    // Verify that `eligibleInstance` is not aliased.
    assert eligibleInstance == eligibleInstance.getAliasedValue();
    replaceUsagesAsUnusedArgument(code);

    boolean anyInlinedMethods = forceInlineExtraMethodInvocations(code, inliningIRProvider);
    if (anyInlinedMethods) {
      // Reset the collections.
      clear();

      // Repeat user analysis
      InstructionOrPhi ineligibleUser = areInstanceUsersEligible(defaultOracle);
      if (ineligibleUser != null) {
        throw new IllegalClassInlinerStateException();
      }
      assert extraMethodCalls.isEmpty()
          : "Remaining extra method calls: " + StringUtils.join(extraMethodCalls.entrySet(), ", ");
      assert unusedArguments.isEmpty()
          : "Remaining unused arg: " + StringUtils.join(unusedArguments, ", ");
    }

    anyInlinedMethods |= forceInlineDirectMethodInvocations(code, inliningIRProvider);
    anyInlinedMethods |= forceInlineIndirectMethodInvocations(code, inliningIRProvider);
    removeAliasIntroducingInstructionsLinkedToEligibleInstance();
    removeMiscUsages(code, affectedValues);
    removeFieldReads(code);
    removeFieldWrites();
    removeInstruction(root);
    return anyInlinedMethods;
  }

  private void clear() {
    methodCallsOnInstance.clear();
    indirectMethodCallsOnInstance.clear();
    extraMethodCalls.clear();
    unusedArguments.clear();
    receivers.reset();
  }

  private void replaceUsagesAsUnusedArgument(IRCode code) {
    for (Pair<InvokeMethod, Integer> unusedArgument : unusedArguments) {
      InvokeMethod invoke = unusedArgument.getFirst();
      BasicBlock block = invoke.getBlock();

      ConstNumber nullValue = code.createConstNull();
      nullValue.setPosition(invoke.getPosition());
      block.listIterator(code, invoke).add(nullValue);
      assert nullValue.getBlock() == block;

      int argIndex = unusedArgument.getSecond();
      invoke.replaceValue(argIndex, nullValue.outValue());
    }
    unusedArguments.clear();
  }

  private boolean forceInlineExtraMethodInvocations(
      IRCode code, InliningIRProvider inliningIRProvider) {
    if (extraMethodCalls.isEmpty()) {
      return false;
    }
    // Inline extra methods.
    inliner.performForcedInlining(
        method, code, extraMethodCalls, inliningIRProvider, Timing.empty());
    return true;
  }

  private boolean forceInlineDirectMethodInvocations(
      IRCode code, InliningIRProvider inliningIRProvider) throws IllegalClassInlinerStateException {
    if (methodCallsOnInstance.isEmpty()) {
      return false;
    }

    assert methodCallsOnInstance.keySet().stream()
        .map(InvokeMethodWithReceiver::getReceiver)
        .allMatch(receivers::isReceiverAlias);

    inliner.performForcedInlining(
        method, code, methodCallsOnInstance, inliningIRProvider, Timing.empty());

    // In case we are class inlining an object allocation that does not inherit directly from
    // java.lang.Object, we need keep force inlining the constructor until we reach
    // java.lang.Object.<init>().
    if (root.isNewInstance()) {
      do {
        methodCallsOnInstance.clear();
        for (Instruction instruction : eligibleInstance.uniqueUsers()) {
          if (instruction.isInvokeDirect()) {
            InvokeDirect invoke = instruction.asInvokeDirect();
            Value receiver = invoke.getReceiver().getAliasedValue();
            if (receiver != eligibleInstance) {
              continue;
            }

            DexMethod invokedMethod = invoke.getInvokedMethod();
            if (invokedMethod == dexItemFactory.objectMembers.constructor) {
              continue;
            }

            if (!dexItemFactory.isConstructor(invokedMethod)) {
              throw new IllegalClassInlinerStateException();
            }

            DexProgramClass holder =
                asProgramClassOrNull(appView.definitionForHolder(invokedMethod));
            if (holder == null) {
              throw new IllegalClassInlinerStateException();
            }

            ProgramMethod singleTarget = holder.lookupProgramMethod(invokedMethod);
            if (singleTarget == null
                || !singleTarget
                    .getDefinition()
                    .isInliningCandidate(
                        method,
                        Reason.SIMPLE,
                        appView.appInfo(),
                        NopWhyAreYouNotInliningReporter.getInstance())) {
              throw new IllegalClassInlinerStateException();
            }

            methodCallsOnInstance.put(invoke, new InliningInfo(singleTarget, eligibleClass.type));
            break;
          }
        }
        if (!methodCallsOnInstance.isEmpty()) {
          inliner.performForcedInlining(
              method, code, methodCallsOnInstance, inliningIRProvider, Timing.empty());
        }
      } while (!methodCallsOnInstance.isEmpty());
    }

    return true;
  }

  private boolean forceInlineIndirectMethodInvocations(
      IRCode code, InliningIRProvider inliningIRProvider) throws IllegalClassInlinerStateException {
    if (indirectMethodCallsOnInstance.isEmpty()) {
      return false;
    }

    Map<InvokeMethodWithReceiver, InliningInfo> methodCallsOnInstance = new IdentityHashMap<>();

    Set<Instruction> currentUsers = eligibleInstance.uniqueUsers();
    while (!currentUsers.isEmpty()) {
      Set<Instruction> indirectOutValueUsers = Sets.newIdentityHashSet();
      for (Instruction instruction : currentUsers) {
        if (instruction.isAssume() || instruction.isCheckCast()) {
          indirectOutValueUsers.addAll(instruction.outValue().uniqueUsers());
          continue;
        }

        if (instruction.isInvokeMethodWithReceiver()) {
          InvokeMethodWithReceiver invoke = instruction.asInvokeMethodWithReceiver();
          DexMethod invokedMethod = invoke.getInvokedMethod();
          if (invokedMethod == dexItemFactory.objectMembers.constructor) {
            continue;
          }

          Value receiver = invoke.getReceiver().getAliasedValue(aliasesThroughAssumeAndCheckCasts);
          if (receiver != eligibleInstance) {
            continue;
          }

          ClassTypeElement exactReceiverType =
              ClassTypeElement.create(eligibleClass.type, Nullability.definitelyNotNull(), appView);
          ProgramMethod singleTarget =
              invoke.lookupSingleProgramTarget(
                  appView, method, exactReceiverType, exactReceiverType);
          if (singleTarget == null || !indirectMethodCallsOnInstance.contains(singleTarget)) {
            throw new IllegalClassInlinerStateException();
          }

          methodCallsOnInstance.put(invoke, new InliningInfo(singleTarget, null));
        }
      }
      currentUsers = indirectOutValueUsers;
    }

    assert !methodCallsOnInstance.isEmpty();

    inliner.performForcedInlining(
        method, code, methodCallsOnInstance, inliningIRProvider, Timing.empty());
    return true;
  }

  private void removeAliasIntroducingInstructionsLinkedToEligibleInstance() {
    Set<Instruction> currentUsers = eligibleInstance.uniqueUsers();
    while (!currentUsers.isEmpty()) {
      Set<Instruction> indirectOutValueUsers = Sets.newIdentityHashSet();
      for (Instruction instruction : currentUsers) {
        if (aliasesThroughAssumeAndCheckCasts.isIntroducingAnAlias(instruction)) {
          Value src = aliasesThroughAssumeAndCheckCasts.getAliasForOutValue(instruction);
          Value dest = instruction.outValue();
          if (dest.hasPhiUsers()) {
            // It is possible that a trivial phi is constructed upon IR building for the eligible
            // value. It must actually be trivial so verify that it is indeed trivial and replace
            // all of the phis involved with the value.
            WorkList<Phi> worklist = WorkList.newIdentityWorkList(dest.uniquePhiUsers());
            while (worklist.hasNext()) {
              Phi phi = worklist.next();
              for (Value operand : phi.getOperands()) {
                operand = operand.getAliasedValue(aliasesThroughAssumeAndCheckCasts);
                if (operand.isPhi()) {
                  worklist.addIfNotSeen(operand.asPhi());
                } else if (src != operand) {
                  throw new InternalCompilerError(
                      "Unexpected non-trivial phi in method eligible for class inlining");
                }
              }
            }
            // The only value flowing into any of the phis is src, so replace all phis by src.
            for (Phi phi : worklist.getSeenSet()) {
              indirectOutValueUsers.addAll(phi.uniqueUsers());
              phi.replaceUsers(src);
              phi.removeDeadPhi();
            }
          }
          assert !dest.hasPhiUsers();
          indirectOutValueUsers.addAll(dest.uniqueUsers());
          dest.replaceUsers(src);
          removeInstruction(instruction);
        }
      }
      currentUsers = indirectOutValueUsers;
    }

    // Verify that no more assume or check-cast instructions are left as users.
    assert eligibleInstance.aliasedUsers().stream().noneMatch(Instruction::isAssume);
    assert eligibleInstance.aliasedUsers().stream().noneMatch(Instruction::isCheckCast);
  }

  // Remove miscellaneous users before handling field reads.
  private void removeMiscUsages(IRCode code, Set<Value> affectedValues) {
    boolean needToRemoveUnreachableBlocks = false;
    for (Instruction user : eligibleInstance.uniqueUsers()) {
      if (user.isInvokeMethod()) {
        InvokeMethod invoke = user.asInvokeMethod();

        // Remove the call to java.lang.Object.<init>().
        if (user.isInvokeDirect()) {
          if (root.isNewInstance()
              && invoke.getInvokedMethod() == dexItemFactory.objectMembers.constructor) {
            removeInstruction(invoke);
            continue;
          }
        }

        if (user.isInvokeStatic()) {
          assert invoke.getInvokedMethod() == dexItemFactory.objectsMethods.requireNonNull;
          removeInstruction(invoke);
          continue;
        }

        DexClassAndMethod singleTarget = invoke.lookupSingleTarget(appView, method);
        if (singleTarget != null && singleTarget.isLibraryMethod()) {
          boolean isSideEffectFree =
              appView
                  .getLibraryMethodSideEffectModelCollection()
                  .isSideEffectFree(invoke, singleTarget.asLibraryMethod());
          if (isSideEffectFree) {
            if (!invoke.hasOutValue() || !invoke.outValue().hasAnyUsers()) {
              removeInstruction(invoke);
              continue;
            }
          }
        }
      }

      if (user.isIf()) {
        If ifInsn = user.asIf();
        assert ifInsn.isZeroTest()
            : "Unexpected usage in non-zero-test IF instruction: " + user;
        BasicBlock block = user.getBlock();
        If.Type type = ifInsn.getType();
        assert type == If.Type.EQ || type == If.Type.NE
            : "Unexpected type in zero-test IF instruction: " + user;
        BasicBlock newBlock = type == If.Type.EQ
            ? ifInsn.fallthroughBlock() : ifInsn.getTrueTarget();
        BasicBlock blockToRemove = type == If.Type.EQ
            ? ifInsn.getTrueTarget() : ifInsn.fallthroughBlock();
        assert newBlock != blockToRemove;

        block.replaceSuccessor(blockToRemove, newBlock);
        blockToRemove.removePredecessor(block, null);
        assert block.exit().isGoto();
        assert block.exit().asGoto().getTarget() == newBlock;
        needToRemoveUnreachableBlocks = true;
        continue;
      }

      if (user.isInstanceGet() || user.isInstancePut()) {
        // Leave field reads/writes until next steps.
        continue;
      }

      if (user.isMonitor()) {
        // Since this instance never escapes and is guaranteed to be non-null, any monitor
        // instructions are no-ops.
        removeInstruction(user);
        continue;
      }

      throw new Unreachable(
          "Unexpected usage left in method `"
              + method.toSourceString()
              + "` after inlining: "
              + user);
    }

    if (needToRemoveUnreachableBlocks) {
      affectedValues.addAll(code.removeUnreachableBlocks());
    }
  }

  // Replace field reads with appropriate values, insert phis when needed.
  private void removeFieldReads(IRCode code) {
    TreeSet<InstanceGet> uniqueInstanceGetUsersWithDeterministicOrder =
        new TreeSet<>(Comparator.comparingInt(x -> x.outValue().getNumber()));
    for (Instruction user : eligibleInstance.uniqueUsers()) {
      if (user.isInstanceGet()) {
        if (user.outValue().hasAnyUsers()) {
          uniqueInstanceGetUsersWithDeterministicOrder.add(user.asInstanceGet());
        } else {
          removeInstruction(user);
        }
        continue;
      }

      if (user.isInstancePut()) {
        // Skip in this iteration since these instructions are needed to properly calculate what
        // value should field reads be replaced with.
        continue;
      }

      throw new Unreachable(
          "Unexpected usage left in method `"
              + method.toSourceString()
              + "` after inlining: "
              + user);
    }

    Map<DexField, FieldValueHelper> fieldHelpers = new IdentityHashMap<>();
    for (InstanceGet user : uniqueInstanceGetUsersWithDeterministicOrder) {
      // Replace a field read with appropriate value.
      replaceFieldRead(code, user, fieldHelpers);
    }
  }

  private void replaceFieldRead(
      IRCode code, InstanceGet fieldRead, Map<DexField, FieldValueHelper> fieldHelpers) {
    Value value = fieldRead.outValue();
    if (value != null) {
      FieldValueHelper helper =
          fieldHelpers.computeIfAbsent(
              fieldRead.getField(), field -> new FieldValueHelper(field, code, root, appView));
      Value newValue = helper.getValueForFieldRead(fieldRead.getBlock(), fieldRead);
      value.replaceUsers(newValue);
      for (FieldValueHelper fieldValueHelper : fieldHelpers.values()) {
        fieldValueHelper.replaceValue(value, newValue);
      }
      assert !value.hasAnyUsers();
      // `newValue` could be a phi introduced by FieldValueHelper. Its initial type is set as the
      // type of read field, but it could be more precise than that due to (multiple) inlining.
      // In addition to values affected by `newValue`, it's necessary to revisit `newValue` itself.
      new TypeAnalysis(appView).narrowing(
          Iterables.concat(ImmutableSet.of(newValue), newValue.affectedValues()));
    }
    removeInstruction(fieldRead);
  }

  private void removeFieldWrites() {
    for (Instruction user : eligibleInstance.uniqueUsers()) {
      if (!user.isInstancePut()) {
        throw new Unreachable(
            "Unexpected usage left in method `"
                + method.toSourceString()
                + "` after field reads removed: "
                + user);
      }
      InstancePut instancePut = user.asInstancePut();
      DexEncodedField field =
          appView
              .appInfo()
              .resolveFieldOn(eligibleClass, instancePut.getField())
              .getResolvedField();
      if (field == null) {
        throw new Unreachable(
            "Unexpected field write left in method `"
                + method.toSourceString()
                + "` after field reads removed: "
                + user);
      }
      removeInstruction(user);
    }
  }

  private InliningInfo isEligibleConstructorCall(InvokeDirect invoke, ProgramMethod singleTarget) {
    assert dexItemFactory.isConstructor(invoke.getInvokedMethod());
    assert isEligibleSingleTarget(singleTarget);

    // Must be a constructor called on the receiver.
    if (!receivers.isDefiniteReceiverAlias(invoke.getReceiver())) {
      return null;
    }

    // None of the subsequent arguments may be an alias of the receiver.
    List<Value> inValues = invoke.inValues();
    for (int i = 1; i < inValues.size(); i++) {
      if (!receivers.addIllegalReceiverAlias(inValues.get(i))) {
        return null;
      }
    }

    // Must be a constructor of the exact same class.
    DexMethod init = invoke.getInvokedMethod();
    if (init.holder != eligibleClass.type) {
      // Calling a constructor on a class that is different from the type of the instance.
      // Gracefully abort class inlining (see the test B116282409).
      return null;
    }

    // Check that the `eligibleInstance` does not escape via the constructor.
    InstanceInitializerInfo instanceInitializerInfo =
        singleTarget.getDefinition().getOptimizationInfo().getInstanceInitializerInfo();
    if (instanceInitializerInfo.receiverMayEscapeOutsideConstructorChain()) {
      return null;
    }

    // Check that the entire constructor chain can be inlined into the current context.
    DexMethod parent = instanceInitializerInfo.getParent();
    while (parent != dexItemFactory.objectMembers.constructor) {
      if (parent == null) {
        return null;
      }
      DexProgramClass parentClass = asProgramClassOrNull(appView.definitionForHolder(parent));
      if (parentClass == null) {
        return null;
      }
      ProgramMethod encodedParent = parentClass.lookupProgramMethod(parent);
      if (encodedParent == null) {
        return null;
      }
      if (methodProcessor.isProcessedConcurrently(encodedParent)) {
        return null;
      }
      DexEncodedMethod encodedParentMethod = encodedParent.getDefinition();
      if (!encodedParentMethod.isInliningCandidate(
          method,
          Reason.SIMPLE,
          appView.appInfo(),
          NopWhyAreYouNotInliningReporter.getInstance())) {
        return null;
      }
      parent = encodedParentMethod.getOptimizationInfo().getInstanceInitializerInfo().getParent();
    }

    return new InliningInfo(singleTarget, eligibleClass.type);
  }

  // An invoke is eligible for inlining in the following cases:
  //
  // - if it does not return the receiver
  // - if there are no uses of the out value
  // - if it is a regular chaining pattern where the only users of the out value are receivers to
  //   other invocations. In that case, we should add all indirect users of the out value to ensure
  //   they can also be inlined.
  private boolean isEligibleInvokeWithAllUsersAsReceivers(
      ClassInlinerEligibilityInfo eligibility,
      InvokeMethodWithReceiver invoke,
      Set<Instruction> indirectUsers) {
    if (eligibility.returnsReceiver.isFalse()) {
      return true;
    }

    Value outValue = invoke.outValue();
    if (outValue == null || !outValue.hasAnyUsers()) {
      return true;
    }

    // For CF we no longer perform the code-rewrite in CodeRewriter.rewriteMoveResult that removes
    // out values if they alias to the receiver since that naively produces a lot of popping values
    // from the stack.
    if (outValue.hasPhiUsers() || outValue.hasDebugUsers()) {
      return false;
    }

    // Add the out-value as a definite-alias if the invoke instruction is guaranteed to return the
    // receiver. Otherwise, the out-value may be an alias of the receiver, and it is added to the
    // may-alias set.
    AliasKind kind = eligibility.returnsReceiver.isTrue() ? AliasKind.DEFINITE : AliasKind.MAYBE;
    if (!receivers.addReceiverAlias(outValue, kind)) {
      return false;
    }

    Set<Instruction> currentUsers = outValue.uniqueUsers();
    while (!currentUsers.isEmpty()) {
      Set<Instruction> indirectOutValueUsers = Sets.newIdentityHashSet();
      for (Instruction instruction : currentUsers) {
        if (instruction.isAssume() || instruction.isCheckCast()) {
          if (instruction.isCheckCast()) {
            CheckCast checkCast = instruction.asCheckCast();
            if (!appView.appInfo().isSubtype(eligibleClass.type, checkCast.getType())) {
              return false; // Unsafe cast.
            }
          }
          Value outValueAlias = instruction.outValue();
          if (outValueAlias.hasPhiUsers() || outValueAlias.hasDebugUsers()) {
            return false;
          }
          if (!receivers.addReceiverAlias(outValueAlias, kind)) {
            return false;
          }
          indirectOutValueUsers.addAll(outValueAlias.uniqueUsers());
          continue;
        }

        if (instruction.isInvokeMethodWithReceiver()) {
          InvokeMethodWithReceiver user = instruction.asInvokeMethodWithReceiver();
          if (user.getReceiver().getAliasedValue(aliasesThroughAssumeAndCheckCasts) != outValue) {
            return false;
          }
          for (int i = 1; i < user.inValues().size(); i++) {
            Value inValue = user.inValues().get(i);
            if (inValue.getAliasedValue(aliasesThroughAssumeAndCheckCasts) == outValue) {
              return false;
            }
          }
          indirectUsers.add(user);
          continue;
        }

        return false;
      }
      currentUsers = indirectOutValueUsers;
    }

    return true;
  }

  private InliningInfo isEligibleDirectVirtualMethodCall(
      InvokeMethodWithReceiver invoke,
      SingleResolutionResult resolutionResult,
      ProgramMethod singleTarget,
      Set<Instruction> indirectUsers,
      Supplier<InliningOracle> defaultOracle) {
    assert isEligibleSingleTarget(singleTarget);

    // None of the none-receiver arguments may be an alias of the receiver.
    List<Value> inValues = invoke.inValues();
    for (int i = 1; i < inValues.size(); i++) {
      if (!receivers.addIllegalReceiverAlias(inValues.get(i))) {
        return null;
      }
    }

    // TODO(b/141719453): Should not constrain library overrides if all instantiations are inlined.
    if (singleTarget.getDefinition().isLibraryMethodOverride().isTrue()) {
      InliningOracle inliningOracle = defaultOracle.get();
      if (!inliningOracle.passesInliningConstraints(
          invoke,
          resolutionResult,
          singleTarget,
          Reason.SIMPLE,
          NopWhyAreYouNotInliningReporter.getInstance())) {
        return null;
      }
    }

    if (!isEligibleVirtualMethodCall(
        invoke,
        invoke.getInvokedMethod(),
        singleTarget,
        eligibility -> isEligibleInvokeWithAllUsersAsReceivers(eligibility, invoke, indirectUsers),
        invoke.getType())) {
      return null;
    }

    ClassInlinerEligibilityInfo eligibility =
        singleTarget.getDefinition().getOptimizationInfo().getClassInlinerEligibility();
    if (eligibility.callsReceiver.size() > 1) {
      return null;
    }
    if (!eligibility.callsReceiver.isEmpty()) {
      assert eligibility.callsReceiver.get(0).getFirst() == Invoke.Type.VIRTUAL;
      Pair<Type, DexMethod> invokeInfo = eligibility.callsReceiver.get(0);
      Type invokeType = invokeInfo.getFirst();
      DexMethod indirectlyInvokedMethod = invokeInfo.getSecond();
      SingleResolutionResult indirectResolutionResult =
          appView
              .appInfo()
              .resolveMethodOn(eligibleClass, indirectlyInvokedMethod)
              .asSingleResolution();
      if (indirectResolutionResult == null) {
        return null;
      }
      ProgramMethod indirectSingleTarget =
          indirectResolutionResult.getResolutionPair().asProgramMethod();
      if (!isEligibleIndirectVirtualMethodCall(
          indirectlyInvokedMethod, invokeType, indirectSingleTarget)) {
        return null;
      }
      indirectMethodCallsOnInstance.add(indirectSingleTarget);
    }

    return new InliningInfo(singleTarget, eligibleClass.type);
  }

  private boolean isEligibleIndirectVirtualMethodCall(DexMethod invokedMethod, Type type) {
    ProgramMethod singleTarget =
        asProgramMethodOrNull(
            appView.appInfo().resolveMethodOn(eligibleClass, invokedMethod).getSingleTarget(),
            appView);
    return isEligibleIndirectVirtualMethodCall(invokedMethod, type, singleTarget);
  }

  private boolean isEligibleIndirectVirtualMethodCall(
      DexMethod invokedMethod, Type type, ProgramMethod singleTarget) {
    if (!isEligibleSingleTarget(singleTarget)) {
      return false;
    }
    if (singleTarget.getDefinition().isLibraryMethodOverride().isTrue()) {
      return false;
    }
    return isEligibleVirtualMethodCall(
        null,
        invokedMethod,
        singleTarget,
        eligibility -> eligibility.callsReceiver.isEmpty() && eligibility.returnsReceiver.isFalse(),
        type);
  }

  private boolean isEligibleVirtualMethodCall(
      InvokeMethodWithReceiver invoke,
      DexMethod callee,
      ProgramMethod singleTarget,
      Predicate<ClassInlinerEligibilityInfo> eligibilityAcceptanceCheck,
      Type type) {
    assert isEligibleSingleTarget(singleTarget);

    // We should not inline a method if the invocation has type interface or virtual and the
    // signature of the invocation resolves to a private or static method.
    // TODO(b/147212189): Why not inline private methods? If access is permitted it is valid.
    ResolutionResult resolutionResult =
        appView.appInfo().resolveMethod(callee, type == Type.INTERFACE);
    if (resolutionResult.isSingleResolution()
        && !resolutionResult.getSingleTarget().isNonPrivateVirtualMethod()) {
      return false;
    }

    if (!singleTarget.getDefinition().isNonPrivateVirtualMethod()) {
      return false;
    }
    if (method.getDefinition() == singleTarget.getDefinition()) {
      return false; // Don't inline itself.
    }

    MethodOptimizationInfo optimizationInfo = singleTarget.getDefinition().getOptimizationInfo();
    ClassInlinerEligibilityInfo eligibility = optimizationInfo.getClassInlinerEligibility();
    if (eligibility == null) {
      return false;
    }

    if (root.isStaticGet()) {
      // If we are class inlining a singleton instance from a static-get, then we don't know the
      // value of the fields.
      ParameterUsage receiverUsage = optimizationInfo.getParameterUsages(0);
      if (receiverUsage == null || receiverUsage.hasFieldRead) {
        return false;
      }
      if (eligibility.hasMonitorOnReceiver) {
        // We will not be able to remove the monitor instruction afterwards.
        return false;
      }
      if (eligibility.modifiesInstanceFields) {
        // The static instance could be accessed from elsewhere. Therefore, we cannot
        // allow side-effects to be removed and therefore cannot class inline method
        // calls that modifies the instance.
        return false;
      }
    }

    // If the method returns receiver and the return value is actually
    // used in the code we need to make some additional checks.
    if (!eligibilityAcceptanceCheck.test(eligibility)) {
      return false;
    }

    markSizeForInlining(invoke, singleTarget);
    return true;
  }

  private boolean isExtraMethodCall(InvokeMethod invoke) {
    if (invoke.isInvokeDirect() && dexItemFactory.isConstructor(invoke.getInvokedMethod())) {
      return false;
    }
    if (invoke.isInvokeMethodWithReceiver()) {
      Value receiver = invoke.asInvokeMethodWithReceiver().getReceiver();
      if (!receivers.addIllegalReceiverAlias(receiver)) {
        return false;
      }
    }
    if (invoke.isInvokeSuper()) {
      return false;
    }
    if (invoke.isInvokePolymorphic()) {
      return false;
    }
    return true;
  }

  // Analyzes if a method invoke the eligible instance is passed to is eligible. In short,
  // it can be eligible if:
  //
  //   -- eligible instance is passed as argument #N which is not used in the method,
  //      such cases are collected in 'unusedArguments' parameter and later replaced
  //      with 'null' value
  //
  //   -- eligible instance is passed as argument #N which is only used in the method to
  //      call a method on this object (we call it indirect method call), and method is
  //      eligible according to the same rules defined for direct method call eligibility
  //      (except we require the method receiver to not be used in return instruction)
  //
  //   -- eligible instance is used in zero-test 'if' instructions testing if the value
  //      is null/not-null (since we know the instance is not null, those checks can
  //      be rewritten)
  //
  //   -- method itself can be inlined
  //
  private boolean isExtraMethodCallEligible(
      InvokeMethod invoke, ProgramMethod singleTarget, Supplier<InliningOracle> defaultOracle) {
    // Don't consider constructor invocations and super calls, since we don't want to forcibly
    // inline them.
    assert isExtraMethodCall(invoke);
    assert isEligibleSingleTarget(singleTarget);

    List<Value> arguments = Lists.newArrayList(invoke.inValues());

    // If we got here with invocation on receiver the user is ineligible.
    if (invoke.isInvokeMethodWithReceiver()) {
      InvokeMethodWithReceiver invokeMethodWithReceiver = invoke.asInvokeMethodWithReceiver();
      Value receiver = invokeMethodWithReceiver.getReceiver();
      if (!receivers.addIllegalReceiverAlias(receiver)) {
        return false;
      }

      // A definitely null receiver will throw an error on call site.
      if (receiver.getType().nullability().isDefinitelyNull()) {
        return false;
      }
    }

    MethodOptimizationInfo optimizationInfo = singleTarget.getDefinition().getOptimizationInfo();

    // Go through all arguments, see if all usages of eligibleInstance are good.
    if (!isEligibleParameterUsages(
        invoke, arguments, singleTarget.getDefinition(), defaultOracle)) {
      return false;
    }

    for (int argIndex = 0; argIndex < arguments.size(); argIndex++) {
      Value argument = arguments.get(argIndex).getAliasedValue();
      ParameterUsage parameterUsage = optimizationInfo.getParameterUsages(argIndex);
      if (receivers.isDefiniteReceiverAlias(argument)
          && parameterUsage != null
          && parameterUsage.notUsed()) {
        // Reference can be removed since it's not used.
        unusedArguments.add(new Pair<>(invoke, argIndex));
      }
    }

    extraMethodCalls.put(invoke, new InliningInfo(singleTarget, null));

    // Looks good.
    markSizeForInlining(invoke, singleTarget);
    return true;
  }

  private boolean isEligibleLibraryMethodCall(InvokeMethod invoke, LibraryMethod singleTarget) {
    boolean isSideEffectFree =
        appView.getLibraryMethodSideEffectModelCollection().isSideEffectFree(invoke, singleTarget);
    if (isSideEffectFree) {
      return !invoke.hasOutValue() || !invoke.outValue().hasAnyUsers();
    }
    if (singleTarget.getReference() == dexItemFactory.objectsMethods.requireNonNull) {
      return !invoke.hasOutValue() || !invoke.outValue().hasAnyUsers();
    }
    return false;
  }

  private boolean isEligibleParameterUsages(
      InvokeMethod invoke,
      List<Value> arguments,
      DexEncodedMethod singleTarget,
      Supplier<InliningOracle> defaultOracle) {
    // Go through all arguments, see if all usages of eligibleInstance are good.
    for (int argIndex = 0; argIndex < arguments.size(); argIndex++) {
      MethodOptimizationInfo optimizationInfo = singleTarget.getOptimizationInfo();
      ParameterUsage parameterUsage = optimizationInfo.getParameterUsages(argIndex);

      Value argument = arguments.get(argIndex);
      if (receivers.isReceiverAlias(argument)) {
        // Have parameter usage info?
        if (!isEligibleParameterUsage(parameterUsage, invoke, defaultOracle)) {
          return false;
        }
      } else {
        // Nothing to worry about, unless `argument` becomes an alias of the receiver later.
        receivers.addDeferredAliasValidityCheck(
            argument, () -> isEligibleParameterUsage(parameterUsage, invoke, defaultOracle));
      }
    }
    return true;
  }

  private boolean isEligibleParameterUsage(
      ParameterUsage parameterUsage, InvokeMethod invoke, Supplier<InliningOracle> defaultOracle) {
    if (parameterUsage == null) {
      return false; // Don't know anything.
    }

    if (parameterUsage.notUsed()) {
      return true;
    }

    if (parameterUsage.isAssignedToField) {
      return false;
    }

    if (root.isStaticGet()) {
      // If we are class inlining a singleton instance from a static-get, then we don't know
      // the value of the fields, and we also can't optimize away instance-field assignments, as
      // they have global side effects.
      if (parameterUsage.hasFieldAssignment || parameterUsage.hasFieldRead) {
        return false;
      }
    }

    if (parameterUsage.isReturned) {
      if (invoke.outValue() != null && invoke.outValue().hasAnyUsers()) {
        // Used as return value which is not ignored.
        return false;
      }
    }

    if (parameterUsage.isUsedInMonitor) {
      return !root.isStaticGet();
    }

    if (!Sets.difference(parameterUsage.ifZeroTest, ALLOWED_ZERO_TEST_TYPES).isEmpty()) {
      // Used in unsupported zero-check-if kinds.
      return false;
    }

    for (Pair<Type, DexMethod> call : parameterUsage.callsReceiver) {
      Type type = call.getFirst();
      DexMethod target = call.getSecond();

      if (type == Type.VIRTUAL || type == Type.INTERFACE) {
        // Is the method called indirectly still eligible?
        if (!isEligibleIndirectVirtualMethodCall(target, type)) {
          return false;
        }
      } else if (type == Type.DIRECT) {
        if (!isInstanceInitializerEligibleForClassInlining(target)) {
          // Only calls to trivial instance initializers are supported at this point.
          return false;
        }
      } else {
        // Static and super calls are not yet supported.
        return false;
      }

      SingleResolutionResult resolutionResult =
          appView
              .appInfo()
              .resolveMethod(invoke.getInvokedMethod(), invoke.getInterfaceBit())
              .asSingleResolution();
      if (resolutionResult == null) {
        return false;
      }

      // Check if the method is inline-able by standard inliner.
      // TODO(b/156853206): Should not duplicate resolution.
      ProgramMethod singleTarget = invoke.lookupSingleProgramTarget(appView, method);
      if (singleTarget == null) {
        return false;
      }

      InliningOracle oracle = defaultOracle.get();
      InlineAction inlineAction =
          oracle.computeInlining(
              invoke,
              resolutionResult,
              singleTarget,
              method,
              ClassInitializationAnalysis.trivial(),
              NopWhyAreYouNotInliningReporter.getInstance());
      if (inlineAction == null) {
        return false;
      }
    }
    return true;
  }

  private boolean isInstanceInitializerEligibleForClassInlining(DexMethod method) {
    if (method == dexItemFactory.objectMembers.constructor) {
      return true;
    }
    DexProgramClass holder = asProgramClassOrNull(appView.definitionForHolder(method));
    DexEncodedMethod definition = method.lookupOnClass(holder);
    if (definition == null) {
      return false;
    }
    InstanceInitializerInfo initializerInfo =
        definition.getOptimizationInfo().getInstanceInitializerInfo();
    return initializerInfo.receiverNeverEscapesOutsideConstructorChain();
  }

  private boolean exemptFromInstructionLimit(ProgramMethod inlinee) {
    KotlinClassLevelInfo kotlinInfo = inlinee.getHolder().getKotlinInfo();
    return kotlinInfo.isSyntheticClass() && kotlinInfo.asSyntheticClass().isLambda();
  }

  private void markSizeForInlining(InvokeMethod invoke, ProgramMethod inlinee) {
    assert !methodProcessor.isProcessedConcurrently(inlinee);
    if (!exemptFromInstructionLimit(inlinee)) {
      if (invoke != null) {
        directInlinees.put(invoke, inlinee);
      } else {
        indirectInlinees.add(inlinee);
      }
    }
  }

  private boolean isEligibleSingleTarget(ProgramMethod singleTarget) {
    if (singleTarget == null) {
      return false;
    }
    if (methodProcessor.isProcessedConcurrently(singleTarget)) {
      return false;
    }
    if (!singleTarget
        .getDefinition()
        .isInliningCandidate(
            method,
            Reason.SIMPLE,
            appView.appInfo(),
            NopWhyAreYouNotInliningReporter.getInstance())) {
      // If `singleTarget` is not an inlining candidate, we won't be able to inline it here.
      //
      // Note that there may be some false negatives here since the method may
      // reference private fields of its class which are supposed to be replaced
      // with arguments after inlining. We should try and improve it later.
      //
      // Using -allowaccessmodification mitigates this.
      return false;
    }
    return true;
  }

  private void removeInstruction(Instruction instruction) {
    instruction.inValues().forEach(v -> v.removeUser(instruction));
    instruction.getBlock().removeInstruction(instruction);
  }

  static class IllegalClassInlinerStateException extends Exception {}
}
