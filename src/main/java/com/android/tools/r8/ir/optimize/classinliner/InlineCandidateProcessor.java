// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.utils.AndroidApiLevelUtils.isApiSafeForInlining;

import com.android.tools.r8.errors.InternalCompilerError;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AccessControl;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.FieldResolutionResult;
import com.android.tools.r8.graph.FieldResolutionResult.SingleProgramFieldResolutionResult;
import com.android.tools.r8.graph.LibraryMethod;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.SingleConstValue;
import com.android.tools.r8.ir.analysis.value.objectstate.ObjectState;
import com.android.tools.r8.ir.code.AliasedValueConfiguration;
import com.android.tools.r8.ir.code.AssumeAndCheckCastAliasedValueConfiguration;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.CheckCast;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.IfType;
import com.android.tools.r8.ir.code.InstanceGet;
import com.android.tools.r8.ir.code.InstanceOf;
import com.android.tools.r8.ir.code.InstancePut;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InstructionOrPhi;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.optimize.AffectedValues;
import com.android.tools.r8.ir.optimize.Inliner;
import com.android.tools.r8.ir.optimize.Inliner.InliningInfo;
import com.android.tools.r8.ir.optimize.Inliner.Reason;
import com.android.tools.r8.ir.optimize.InliningOracle;
import com.android.tools.r8.ir.optimize.classinliner.ClassInliner.EligibilityStatus;
import com.android.tools.r8.ir.optimize.classinliner.analysis.NonEmptyParameterUsage;
import com.android.tools.r8.ir.optimize.classinliner.analysis.ParameterUsage;
import com.android.tools.r8.ir.optimize.classinliner.constraint.ClassInlinerMethodConstraint;
import com.android.tools.r8.ir.optimize.info.FieldOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.MethodOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.initializer.InstanceInitializerInfo;
import com.android.tools.r8.ir.optimize.inliner.InliningIRProvider;
import com.android.tools.r8.ir.optimize.inliner.NopWhyAreYouNotInliningReporter;
import com.android.tools.r8.kotlin.KotlinClassLevelInfo;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ArrayUtils;
import com.android.tools.r8.utils.LazyBox;
import com.android.tools.r8.utils.OptionalBool;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.WorkList;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

final class InlineCandidateProcessor {

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
  private ObjectState objectState;

  private final Map<InvokeMethod, InliningInfo> directMethodCalls = new IdentityHashMap<>();

  private final ProgramMethodSet indirectMethodCallsOnInstance = ProgramMethodSet.create();

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
      if (method.getHolder() == eligibleClass) {
        return EligibilityStatus.NOT_ELIGIBLE;
      }
      if (eligibleClass.classInitializationMayHaveSideEffectsInContext(appView, method)) {
        return EligibilityStatus.NOT_ELIGIBLE;
      }
      return EligibilityStatus.ELIGIBLE;
    }

    assert root.isStaticGet();

    StaticGet staticGet = root.asStaticGet();
    SingleProgramFieldResolutionResult fieldResolutionResult =
        appView.appInfo().resolveField(staticGet.getField()).asSingleProgramFieldResolutionResult();
    if (fieldResolutionResult == null) {
      return EligibilityStatus.NOT_ELIGIBLE;
    }
    if (method.getHolder() == fieldResolutionResult.getResolvedHolder()) {
      return EligibilityStatus.NOT_ELIGIBLE;
    }
    if (staticGet.instructionMayHaveSideEffects(appView, method)) {
      return EligibilityStatus.NOT_ELIGIBLE;
    }
    DexEncodedField field = fieldResolutionResult.getResolvedField();
    FieldOptimizationInfo optimizationInfo = field.getOptimizationInfo();
    DynamicType dynamicType = optimizationInfo.getDynamicType();
    if (!dynamicType.isExactClassType() || !dynamicType.getNullability().isDefinitelyNotNull()) {
      return EligibilityStatus.NOT_ELIGIBLE;
    }
    eligibleClass =
        asProgramClassOrNull(appView.definitionFor(dynamicType.getExactClassType().getClassType()));
    if (eligibleClass == null) {
      return EligibilityStatus.NOT_ELIGIBLE;
    }
    AbstractValue abstractValue = optimizationInfo.getAbstractValue();
    objectState =
        abstractValue.isSingleFieldValue()
            ? abstractValue.asSingleFieldValue().getObjectState()
            : ObjectState.empty();
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
  InstructionOrPhi areInstanceUsersEligible(LazyBox<InliningOracle> defaultOracle) {
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
            CheckCast checkCast = user.asCheckCast();
            // TODO(b/175863158): Allow unsafe casts by rewriting into throw new ClassCastException.
            boolean isCheckCastUnsafe =
                !checkCast.getType().isClassType()
                    || !appView.appInfo().isSubtype(eligibleClass.type, checkCast.getType());
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
          if (!receivers.addReceiverAlias(alias)) {
            return user; // Not eligible.
          }
          indirectUsers.addAll(alias.uniqueUsers());
          continue;
        }

        if (user.isInstanceGet()) {
          FieldResolutionResult resolutionResult =
              appView.appInfo().resolveField(user.asFieldInstruction().getField());
          if (!resolutionResult.isSingleFieldResolutionResult()
              || resolutionResult.isAccessibleFrom(method, appView).isPossiblyFalse()) {
            return user; // Not eligible.
          }
          DexEncodedField field = resolutionResult.getResolvedField();
          if (field == null || field.isStatic()) {
            return user; // Not eligible.
          }
          if (root.isStaticGet()
              && !objectState.hasMaterializableFieldValueThatMatches(
                  appView, field, method, AbstractValue::isSingleConstValue)) {
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
          InvokeMethod invoke = user.asInvokeMethod();
          SingleResolutionResult<?> resolutionResult =
              appView
                  .appInfo()
                  .resolveMethodLegacy(invoke.getInvokedMethod(), invoke.getInterfaceBit())
                  .asSingleResolution();
          if (resolutionResult == null
              || resolutionResult.isAccessibleFrom(method, appView).isPossiblyFalse()) {
            return user; // Not eligible.
          }

          // TODO(b/156853206): Avoid duplicating resolution.
          DexClassAndMethod singleTarget = invoke.lookupSingleTarget(appView, method);
          if (singleTarget == null) {
            return user; // Not eligible.
          }

          if (singleTarget.isLibraryMethod()
              && isEligibleLibraryMethodCall(invoke, singleTarget.asLibraryMethod())) {
            continue;
          }

          ProgramMethod singleProgramTarget = singleTarget.asProgramMethod();
          if (!isEligibleSingleTarget(singleProgramTarget)) {
            return user; // Not eligible.
          }

          // The target access is checked in isEligibleSingleTarget above.
          assert AccessControl.isClassAccessible(singleProgramTarget.getHolder(), method, appView)
              .isTrue();

          // Eligible constructor call (for new instance roots only).
          if (user.isInvokeConstructor(dexItemFactory)) {
            InvokeDirect invokeDirect = user.asInvokeDirect();
            boolean isCorrespondingConstructorCall =
                root.isNewInstance() && root.outValue() == invokeDirect.getReceiver();
            if (isCorrespondingConstructorCall) {
              InliningInfo inliningInfo =
                  isEligibleConstructorCall(invokeDirect, singleProgramTarget);
              if (inliningInfo != null) {
                directMethodCalls.put(invoke, inliningInfo);
                continue;
              }
            }
            return user; // Not eligible.
          }

          // Eligible non-constructor method call.
          if (isEligibleDirectMethodCall(
              invoke, resolutionResult, singleProgramTarget, defaultOracle, indirectUsers)) {
            continue;
          }

          return user; // Not eligible.
        }

        // Allow some IF instructions.
        if (user.isIf()) {
          If ifInsn = user.asIf();
          IfType type = ifInsn.getType();
          if (ifInsn.isZeroTest() && (type == IfType.EQ || type == IfType.NE)) {
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
  //  * inline extra methods if any, collect new direct method calls
  //  * inline direct methods if any
  //  * remove superclass initializer call and field reads
  //  * remove field writes
  //  * remove root instruction
  //
  // Returns `true` if at least one method was inlined.
  boolean processInlining(
      IRCode code, AffectedValues affectedValues, InliningIRProvider inliningIRProvider)
      throws IllegalClassInlinerStateException {
    // Verify that `eligibleInstance` is not aliased.
    assert eligibleInstance == eligibleInstance.getAliasedValue();

    boolean anyInlinedMethods = forceInlineDirectMethodInvocations(code, inliningIRProvider);
    anyInlinedMethods |= forceInlineIndirectMethodInvocations(code, inliningIRProvider);

    rebindIndirectEligibleInstanceUsersFromPhis();
    removeMiscUsages(code, affectedValues);
    removeFieldReads(code, affectedValues);
    removeFieldWrites();
    removeInstruction(root);
    return anyInlinedMethods;
  }

  @SuppressWarnings("ReferenceEquality")
  private boolean forceInlineDirectMethodInvocations(
      IRCode code, InliningIRProvider inliningIRProvider) throws IllegalClassInlinerStateException {
    if (directMethodCalls.isEmpty()) {
      return false;
    }

    inliner.performForcedInlining(
        method, code, directMethodCalls, inliningIRProvider, methodProcessor, Timing.empty());

    // In case we are class inlining an object allocation that does not inherit directly from
    // java.lang.Object, we need keep force inlining the constructor until we reach
    // java.lang.Object.<init>().
    if (root.isNewInstance()) {
      do {
        directMethodCalls.clear();
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
                asProgramClassOrNull(appView.definitionForHolder(invokedMethod, method));
            if (holder == null) {
              throw new IllegalClassInlinerStateException();
            }

            ProgramMethod singleTarget = holder.lookupProgramMethod(invokedMethod);
            if (singleTarget == null
                || !singleTarget
                    .getDefinition()
                    .isInliningCandidate(
                        method,
                        Reason.ALWAYS,
                        appView.appInfo(),
                        NopWhyAreYouNotInliningReporter.getInstance())) {
              throw new IllegalClassInlinerStateException();
            }

            directMethodCalls.put(invoke, new InliningInfo(singleTarget, eligibleClass));
            break;
          }
        }
        if (!directMethodCalls.isEmpty()) {
          inliner.performForcedInlining(
              method, code, directMethodCalls, inliningIRProvider, methodProcessor, Timing.empty());
        }
      } while (!directMethodCalls.isEmpty());
    }

    return true;
  }

  @SuppressWarnings("ReferenceEquality")
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

          DynamicType exactReceiverType =
              DynamicType.createExact(
                  ClassTypeElement.create(
                      eligibleClass.getType(), Nullability.definitelyNotNull(), appView));
          ProgramMethod singleTarget =
              invoke.lookupSingleProgramTarget(appView, method, exactReceiverType);
          if (singleTarget == null || !indirectMethodCallsOnInstance.contains(singleTarget)) {
            throw new IllegalClassInlinerStateException();
          }

          methodCallsOnInstance.put(invoke, new InliningInfo(singleTarget, null));
        }
      }
      currentUsers = indirectOutValueUsers;
    }

    if (!methodCallsOnInstance.isEmpty()) {
      inliner.performForcedInlining(
          method, code, methodCallsOnInstance, inliningIRProvider, methodProcessor, Timing.empty());
    } else {
      assert indirectMethodCallsOnInstance.stream()
          .filter(method -> method.getDefinition().getOptimizationInfo().mayHaveSideEffects())
          .allMatch(
              method ->
                  method.getDefinition().isInstanceInitializer()
                      && !method
                          .getDefinition()
                          .getOptimizationInfo()
                          .getContextInsensitiveInstanceInitializerInfo()
                          .mayHaveOtherSideEffectsThanInstanceFieldAssignments());
    }
    return true;
  }

  private void rebindIndirectEligibleInstanceUsersFromPhis() {
    // Building the inlinee can cause some of the eligibleInstance users to be phi's. These phi's
    // should be trivial.
    // block X:
    // vX <- NewInstance ...
    // block Y:
    // vZ : phi(vX, vY)
    // block Z
    // vY : phi(vX, vZ)
    // These are not pruned by the trivial phi removal. We have to ensure that we rewrite all users
    // also the indirect users directly using phi's, potentially through assumes and checkcast.
    Set<Value> aliases = SetUtils.newIdentityHashSet(eligibleInstance);
    Set<Phi> expectedDeadOrTrivialPhis = Sets.newIdentityHashSet();
    WorkList<InstructionOrPhi> worklist = WorkList.newIdentityWorkList();
    eligibleInstance.uniqueUsers().forEach(worklist::addIfNotSeen);
    eligibleInstance.uniquePhiUsers().forEach(worklist::addIfNotSeen);
    while (worklist.hasNext()) {
      InstructionOrPhi instructionOrPhi = worklist.next();
      if (instructionOrPhi.isPhi()) {
        Phi phi = instructionOrPhi.asPhi();
        expectedDeadOrTrivialPhis.add(phi);
        phi.uniqueUsers().forEach(worklist::addIfNotSeen);
        phi.uniquePhiUsers().forEach(worklist::addIfNotSeen);
      } else {
        Instruction instruction = instructionOrPhi.asInstruction();
        if (aliasesThroughAssumeAndCheckCasts.isIntroducingAnAlias(instruction)) {
          aliases.add(instruction.outValue());
          instruction.outValue().uniqueUsers().forEach(worklist::addIfNotSeen);
          instruction.outValue().uniquePhiUsers().forEach(worklist::addIfNotSeen);
        }
      }
    }
    // Check that all phis are dead or trivial.
    for (Phi deadTrivialPhi : expectedDeadOrTrivialPhis) {
      for (Value operand : deadTrivialPhi.getOperands()) {
        operand = operand.getAliasedValue(aliasesThroughAssumeAndCheckCasts);
        // If the operand is a phi we should have found it in the search above.
        if (operand.isPhi() && !expectedDeadOrTrivialPhis.contains(operand.asPhi())) {
          throw new InternalCompilerError(
              "Unexpected non-trivial phi in method eligible for class inlining");
        }
        // If the operand is not a phi, it should be an alias (or the eligibleInstance).
        if (!operand.isPhi() && !aliases.contains(operand)) {
          throw new InternalCompilerError(
              "Unexpected non-trivial phi in method eligible for class inlining");
        }
      }
      deadTrivialPhi.replaceUsers(eligibleInstance);
      deadTrivialPhi.removeDeadPhi();
    }
    // We can now prune the aliases
    for (Value alias : aliases) {
      if (alias == eligibleInstance) {
        continue;
      }
      assert alias.definition.isAssume() || alias.definition.isCheckCast();
      alias.replaceUsers(eligibleInstance);
      removeInstruction(alias.definition);
    }
    // Verify that no more assume or check-cast instructions are left as users.
    assert eligibleInstance.aliasedUsers().stream().noneMatch(Instruction::isAssume);
    assert eligibleInstance.aliasedUsers().stream().noneMatch(Instruction::isCheckCast);
  }

  @SuppressWarnings("ReferenceEquality")
  // Remove miscellaneous users before handling field reads.
  private void removeMiscUsages(IRCode code, AffectedValues affectedValues) {
    boolean needToRemoveUnreachableBlocks = false;
    for (Instruction user : eligibleInstance.uniqueUsers()) {
      if (user.isInstanceOf()) {
        InstanceOf instanceOf = user.asInstanceOf();
        InstructionListIterator instructionIterator =
            user.getBlock().listIterator(code, instanceOf);
        instructionIterator.replaceCurrentInstructionWithConstBoolean(
            code, appView.appInfo().isSubtype(eligibleClass.getType(), instanceOf.type()));
        continue;
      }

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
        IfType type = ifInsn.getType();
        assert type == IfType.EQ || type == IfType.NE
            : "Unexpected type in zero-test IF instruction: " + user;
        BasicBlock newBlock =
            type == IfType.EQ ? ifInsn.fallthroughBlock() : ifInsn.getTrueTarget();
        BasicBlock blockToRemove =
            type == IfType.EQ ? ifInsn.getTrueTarget() : ifInsn.fallthroughBlock();
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
  private void removeFieldReads(IRCode code, AffectedValues affectedValues) {
    if (root.isNewInstance()) {
      removeFieldReadsFromNewInstance(code, affectedValues);
    } else {
      assert root.isStaticGet();
      removeFieldReadsFromStaticGet(code, affectedValues);
    }
  }

  private void removeFieldReadsFromNewInstance(IRCode code, AffectedValues affectedValues) {
    List<InstanceGet> uniqueInstanceGetUsersWithDeterministicOrder = new ArrayList<>();
    for (Instruction user : eligibleInstance.uniqueUsers()) {
      if (user.isInstanceGet()) {
        if (user.hasUsedOutValue()) {
          uniqueInstanceGetUsersWithDeterministicOrder.add(user.asInstanceGet());
        } else {
          removeInstruction(user);
        }
        continue;
      }

      if (user.isInstancePut()) {
        // Skip in this iteration since these instructions are needed to properly calculate what
        // value should field reads be replaced with.
        assert root.isNewInstance();
        continue;
      }

      throw new Unreachable(
          "Unexpected usage left in method `"
              + method.toSourceString()
              + "` after inlining: "
              + user);
    }

    uniqueInstanceGetUsersWithDeterministicOrder.sort(Comparator.comparing(Instruction::outValue));
    Map<DexField, FieldValueHelper> fieldHelpers = new IdentityHashMap<>();
    for (InstanceGet user : uniqueInstanceGetUsersWithDeterministicOrder) {
      // Replace a field read with appropriate value.
      removeFieldReadFromNewInstance(code, user, affectedValues, fieldHelpers);
    }
  }

  private void removeFieldReadFromNewInstance(
      IRCode code,
      InstanceGet fieldRead,
      AffectedValues affectedValues,
      Map<DexField, FieldValueHelper> fieldHelpers) {
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
      affectedValues.add(newValue);
      affectedValues.addAll(newValue.affectedValues());
    }
    removeInstruction(fieldRead);
  }

  private void removeFieldReadsFromStaticGet(IRCode code, AffectedValues affectedValues) {
    Set<BasicBlock> seen = Sets.newIdentityHashSet();
    Set<Instruction> users = eligibleInstance.uniqueUsers();
    Map<InstanceGet, Instruction[]> pendingReplacements = new IdentityHashMap<>();
    for (Instruction user : users) {
      if (!user.hasBlock()) {
        continue;
      }

      BasicBlock block = user.getBlock();
      if (!seen.add(block)) {
        continue;
      }

      InstructionListIterator instructionIterator = block.listIterator(code);
      while (instructionIterator.hasNext()) {
        Instruction instruction = instructionIterator.next();
        if (!users.contains(instruction)) {
          continue;
        }

        if (instruction.isInstanceGet()) {
          if (instruction.hasUsedOutValue()) {
            replaceFieldReadFromStaticGet(
                code,
                instructionIterator,
                user.asInstanceGet(),
                affectedValues,
                pendingReplacements);
          } else {
            instructionIterator.removeOrReplaceByDebugLocalRead();
          }
          continue;
        }

        if (instruction.isInstancePut()) {
          instructionIterator.removeOrReplaceByDebugLocalRead();
          continue;
        }

        throw new Unreachable(
            "Unexpected usage left in method `"
                + method.toSourceString()
                + "` after inlining: "
                + user);
      }
    }

    if (!pendingReplacements.isEmpty()) {
      BasicBlockIterator blocks = code.listIterator();
      while (blocks.hasNext()) {
        BasicBlock block = blocks.next();
        InstructionListIterator instructionIterator = block.listIterator(code);
        while (instructionIterator.hasNext()) {
          Instruction instruction = instructionIterator.next();
          Instruction[] replacementInstructions = pendingReplacements.get(instruction);
          if (replacementInstructions == null) {
            continue;
          }
          assert replacementInstructions.length > 1;
          Instruction replacement = ArrayUtils.last(replacementInstructions);
          instruction.outValue().replaceUsers(replacement.outValue(), affectedValues);
          instructionIterator.removeOrReplaceByDebugLocalRead();
          instructionIterator =
              instructionIterator.addPossiblyThrowingInstructionsToPossiblyThrowingBlock(
                  code, blocks, replacementInstructions, appView.options());
        }
      }
    }
  }

  private void replaceFieldReadFromStaticGet(
      IRCode code,
      InstructionListIterator instructionIterator,
      InstanceGet fieldRead,
      AffectedValues affectedValues,
      Map<InstanceGet, Instruction[]> pendingReplacements) {
    DexField fieldReference = fieldRead.getField();
    DexClass holder = appView.definitionFor(fieldReference.getHolderType(), method);
    DexEncodedField field = fieldReference.lookupOnClass(holder);
    if (field == null) {
      throw reportUnknownFieldReadFromSingleton(fieldRead);
    }

    AbstractValue abstractValue = objectState.getAbstractFieldValue(field);
    if (!abstractValue.isSingleConstValue()) {
      throw reportUnknownFieldReadFromSingleton(fieldRead);
    }

    SingleConstValue singleConstValue = abstractValue.asSingleConstValue();
    if (!singleConstValue.isMaterializableInContext(appView, method)) {
      throw reportUnknownFieldReadFromSingleton(fieldRead);
    }

    Instruction[] materializingInstructions =
        singleConstValue.createMaterializingInstructions(appView, code, fieldRead);
    Instruction replacement = ArrayUtils.last(materializingInstructions);
    if (materializingInstructions.length == 1) {
      instructionIterator.replaceCurrentInstruction(replacement, affectedValues);
    } else {
      pendingReplacements.put(fieldRead, materializingInstructions);
    }
  }

  private RuntimeException reportUnknownFieldReadFromSingleton(InstanceGet fieldRead) {
    throw appView
        .reporter()
        .fatalError(
            "Unexpected usage left in method `"
                + method.toSourceString()
                + "` after inlining: "
                + fieldRead.toString());
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

      assert root.isNewInstance();

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

  @SuppressWarnings("ReferenceEquality")
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

    // Must be a constructor of the exact same class except when targeting ART, where constructors
    // in the superclass hierarchy are also allowed.
    DexMethod init = invoke.getInvokedMethod();
    if (appView.options().canInitNewInstanceUsingSuperclassConstructor()) {
      if (!appView.appInfo().isSubtype(eligibleClass.getType(), init.getHolderType())) {
        return null;
      }
    } else {
      if (eligibleClass.getType() != init.getHolderType()) {
        return null;
      }
    }

    // Check that the `eligibleInstance` does not escape via the constructor.
    InstanceInitializerInfo instanceInitializerInfo =
        singleTarget.getDefinition().getOptimizationInfo().getInstanceInitializerInfo(invoke);
    if (instanceInitializerInfo.receiverMayEscapeOutsideConstructorChain()) {
      return null;
    }
    // Check the api level is allowed to be inlined.
    if (!isApiSafeForInlining(method, singleTarget, appView.options())) {
      return null;
    }
    // Check that the entire constructor chain can be inlined into the current context.
    DexMethod parent = instanceInitializerInfo.getParent();
    while (parent != dexItemFactory.objectMembers.constructor) {
      if (parent == null) {
        return null;
      }
      DexProgramClass parentClass =
          asProgramClassOrNull(appView.definitionForHolder(parent, method));
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
          Reason.ALWAYS,
          appView.appInfo(),
          NopWhyAreYouNotInliningReporter.getInstance())) {
        return null;
      }
      // Check the api level is allowed to be inlined.
      if (!isApiSafeForInlining(method, encodedParent, appView.options())) {
        return null;
      }
      parent =
          encodedParentMethod
              .getOptimizationInfo()
              .getContextInsensitiveInstanceInitializerInfo()
              .getParent();
    }

    return new InliningInfo(singleTarget, eligibleClass);
  }

  // An invoke is eligible for inlining in the following cases:
  //
  // - if it does not return the receiver
  // - if there are no uses of the out value
  // - if it is a regular chaining pattern where the only users of the out value are receivers to
  //   other invocations. In that case, we should add all indirect users of the out value to ensure
  //   they can also be inlined.
  private boolean scheduleNewUsersForAnalysis(
      InvokeMethod invoke,
      ProgramMethod singleTarget,
      int parameter,
      Set<Instruction> indirectUsers) {
    ClassInlinerMethodConstraint classInlinerMethodConstraint =
        singleTarget.getDefinition().getOptimizationInfo().getClassInlinerMethodConstraint();
    ParameterUsage usage = classInlinerMethodConstraint.getParameterUsage(parameter);

    OptionalBool returnsParameter;
    if (usage.isParameterReturned()) {
      if (singleTarget.getDefinition().getOptimizationInfo().returnsArgument()) {
        assert singleTarget.getDefinition().getOptimizationInfo().getReturnedArgument()
            == parameter;
        returnsParameter = OptionalBool.TRUE;
      } else {
        returnsParameter = OptionalBool.UNKNOWN;
      }
    } else {
      returnsParameter = OptionalBool.FALSE;
    }

    if (returnsParameter.isFalse()) {
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

    // We cannot guarantee the invoke returns the receiver or another instance and since the
    // return value is used we have to bail out.
    if (returnsParameter.isUnknown()) {
      return false;
    }

    // Add the out-value as a definite-alias if the invoke instruction is guaranteed to return the
    // receiver. Otherwise, the out-value may be an alias of the receiver, and it is added to the
    // may-alias set.
    assert returnsParameter.isTrue();
    if (!receivers.addReceiverAlias(outValue)) {
      return false;
    }

    indirectUsers.addAll(outValue.uniqueUsers());
    return true;
  }


  private boolean isEligibleIndirectVirtualMethodCall(
      DexMethod invokedMethod, ProgramMethod singleTarget) {
    if (!isEligibleSingleTarget(singleTarget)) {
      return false;
    }
    if (singleTarget.getDefinition().isLibraryMethodOverride().isTrue()) {
      return false;
    }
    if (!isEligibleVirtualMethodCall(invokedMethod, singleTarget)) {
      return false;
    }

    ParameterUsage usage =
        singleTarget
            .getDefinition()
            .getOptimizationInfo()
            .getClassInlinerMethodConstraint()
            .getParameterUsage(0);
    assert !usage.isTop();
    if (usage.isBottom()) {
      return true;
    }
    NonEmptyParameterUsage nonEmptyUsage = usage.asNonEmpty();
    return nonEmptyUsage.getMethodCallsWithParameterAsReceiver().isEmpty()
        && !nonEmptyUsage.isParameterReturned();
  }

  @SuppressWarnings("ReferenceEquality")
  private boolean isEligibleVirtualMethodCall(DexMethod callee, ProgramMethod singleTarget) {
    assert isEligibleSingleTarget(singleTarget);

    // We should not inline a method if the invocation has type interface or virtual and the
    // signature of the invocation resolves to a private or static method.
    // TODO(b/147212189): Why not inline private methods? If access is permitted it is valid.
    MethodResolutionResult resolutionResult =
        appView.appInfo().resolveMethodOnClassLegacy(eligibleClass, callee);
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
    ClassInlinerMethodConstraint classInlinerMethodConstraint =
        optimizationInfo.getClassInlinerMethodConstraint();
    int parameter = 0;
    if (root.isNewInstance()) {
      return classInlinerMethodConstraint.isEligibleForNewInstanceClassInlining(
          appView, eligibleClass, singleTarget, parameter);
    }

    assert root.isStaticGet();
    return classInlinerMethodConstraint.isEligibleForStaticGetClassInlining(
        appView, eligibleClass, parameter, objectState, method);
  }

  // Analyzes if a method invoke the eligible instance is passed to is eligible. In short,
  // it can be eligible if:
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
  private boolean isEligibleDirectMethodCall(
      InvokeMethod invoke,
      SingleResolutionResult<?> resolutionResult,
      ProgramMethod singleTarget,
      LazyBox<InliningOracle> defaultOracle,
      Set<Instruction> indirectUsers) {
    if (!((invoke.isInvokeDirect() && !invoke.isInvokeConstructor(dexItemFactory))
        || invoke.isInvokeInterface()
        || invoke.isInvokeStatic()
        || invoke.isInvokeVirtual())) {
      return false;
    }

    // If we got here with invocation on receiver the user is ineligible.
    if (invoke.isInvokeMethodWithReceiver()) {
      // A definitely null receiver will throw an error on call site.
      Value receiver = invoke.asInvokeMethodWithReceiver().getReceiver();
      if (receiver.getType().isDefinitelyNull()) {
        return false;
      }
    }

    // Check if the method is inline-able by standard inliner.
    InliningOracle oracle = defaultOracle.computeIfAbsent();
    if (!oracle.passesInliningConstraints(
        invoke,
        resolutionResult,
        singleTarget,
        Reason.ALWAYS,
        NopWhyAreYouNotInliningReporter.getInstance())) {
      return false;
    }

    // Go through all arguments, see if all usages of eligibleInstance are good.
    if (!isEligibleParameterUsages(invoke, singleTarget, indirectUsers)) {
      return false;
    }

    directMethodCalls.put(invoke, new InliningInfo(singleTarget, null));

    // Looks good.
    markSizeOfDirectTargetForInlining(invoke, singleTarget);
    return true;
  }

  @SuppressWarnings("ReferenceEquality")
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
      InvokeMethod invoke, ProgramMethod singleTarget, Set<Instruction> indirectUsers) {
    // Go through all arguments, see if all usages of eligibleInstance are good.
    for (int parameter = 0; parameter < invoke.arguments().size(); parameter++) {
      Value argument = invoke.getArgument(parameter);
      if (receivers.isReceiverAlias(argument)) {
        // Have parameter usage info?
        if (!isEligibleParameterUsage(invoke, singleTarget, parameter, indirectUsers)) {
          return false;
        }
      } else {
        // Nothing to worry about, unless `argument` becomes an alias of the receiver later.
        int finalParameter = parameter;
        receivers.addDeferredAliasValidityCheck(
            argument,
            () -> isEligibleParameterUsage(invoke, singleTarget, finalParameter, indirectUsers));
      }
    }
    return true;
  }

  private boolean isEligibleParameterUsage(
      InvokeMethod invoke,
      ProgramMethod singleTarget,
      int parameter,
      Set<Instruction> indirectUsers) {
    ClassInlinerMethodConstraint classInlinerMethodConstraint =
        singleTarget.getDefinition().getOptimizationInfo().getClassInlinerMethodConstraint();
    if (root.isNewInstance()) {
      if (!classInlinerMethodConstraint.isEligibleForNewInstanceClassInlining(
          appView, eligibleClass, singleTarget, parameter)) {
        return false;
      }
    } else {
      assert root.isStaticGet();
      if (!classInlinerMethodConstraint.isEligibleForStaticGetClassInlining(
          appView, eligibleClass, parameter, objectState, method)) {
        return false;
      }
    }

    ParameterUsage usage = classInlinerMethodConstraint.getParameterUsage(parameter);
    if (!scheduleNewUsersForAnalysis(invoke, singleTarget, parameter, indirectUsers)) {
      return false;
    }

    if (!usage.isBottom()) {
      NonEmptyParameterUsage nonEmptyUsage = usage.asNonEmpty();
      for (DexMethod invokedMethod : nonEmptyUsage.getMethodCallsWithParameterAsReceiver()) {
        SingleResolutionResult<?> resolutionResult =
            appView
                .appInfo()
                .resolveMethodOnLegacy(eligibleClass, invokedMethod)
                .asSingleResolution();
        if (resolutionResult == null || !resolutionResult.getResolvedHolder().isProgramClass()) {
          return false;
        }

        // Is the method called indirectly still eligible?
        ProgramMethod indirectSingleTarget = resolutionResult.getResolutionPair().asProgramMethod();
        if (!isEligibleIndirectVirtualMethodCall(invokedMethod, indirectSingleTarget)) {
          return false;
        }
        markSizeOfIndirectTargetForInlining(indirectSingleTarget);
      }
    }

    return true;
  }

  private boolean exemptFromInstructionLimit(ProgramMethod inlinee) {
    KotlinClassLevelInfo kotlinInfo = inlinee.getHolder().getKotlinInfo();
    return kotlinInfo.isSyntheticClass() && kotlinInfo.asSyntheticClass().isLambda();
  }

  private void markSizeOfIndirectTargetForInlining(ProgramMethod inlinee) {
    assert !methodProcessor.isProcessedConcurrently(inlinee);
    if (!exemptFromInstructionLimit(inlinee)) {
      indirectInlinees.add(inlinee);
    }
    indirectMethodCallsOnInstance.add(inlinee);
  }

  private void markSizeOfDirectTargetForInlining(InvokeMethod invoke, ProgramMethod inlinee) {
    assert invoke != null;
    assert !methodProcessor.isProcessedConcurrently(inlinee);
    if (!exemptFromInstructionLimit(inlinee)) {
      directInlinees.put(invoke, inlinee);
    }
  }

  private boolean isEligibleSingleTarget(ProgramMethod singleTarget) {
    if (singleTarget == null) {
      return false;
    }
    if (methodProcessor.isProcessedConcurrently(singleTarget)) {
      return false;
    }
    if (AccessControl.isMemberAccessible(singleTarget, singleTarget.getHolder(), method, appView)
        .isPossiblyFalse()) {
      return false;
    }
    if (!appView.getKeepInfo(singleTarget).isClassInliningAllowed(appView.options())) {
      return false;
    }
    if (!singleTarget
        .getDefinition()
        .isInliningCandidate(
            method,
            Reason.ALWAYS,
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

  static class IllegalClassInlinerStateException extends Exception {

    IllegalClassInlinerStateException() {
      assert false;
    }
  }
}
