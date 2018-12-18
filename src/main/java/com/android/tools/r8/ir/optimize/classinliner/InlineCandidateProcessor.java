// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfo.ResolutionResult;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexEncodedMethod.ClassInlinerEligibility;
import com.android.tools.r8.graph.DexEncodedMethod.TrivialInitializer;
import com.android.tools.r8.graph.DexEncodedMethod.TrivialInitializer.TrivialClassInitializer;
import com.android.tools.r8.graph.DexEncodedMethod.TrivialInitializer.TrivialInstanceInitializer;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.OptimizationInfo;
import com.android.tools.r8.graph.ParameterUsagesInfo.ParameterUsage;
import com.android.tools.r8.ir.analysis.type.TypeAnalysis;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.InstanceGet;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionOrPhi;
import com.android.tools.r8.ir.code.Invoke.Type;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.desugar.LambdaRewriter;
import com.android.tools.r8.ir.optimize.Inliner;
import com.android.tools.r8.ir.optimize.Inliner.InlineAction;
import com.android.tools.r8.ir.optimize.Inliner.InliningInfo;
import com.android.tools.r8.ir.optimize.Inliner.Reason;
import com.android.tools.r8.ir.optimize.InliningOracle;
import com.android.tools.r8.kotlin.KotlinInfo;
import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;
import com.android.tools.r8.utils.Pair;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

final class InlineCandidateProcessor {
  private static final ImmutableSet<If.Type> ALLOWED_ZERO_TEST_TYPES =
      ImmutableSet.of(If.Type.EQ, If.Type.NE);

  private final DexItemFactory factory;
  private final AppInfoWithLiveness appInfo;
  private final LambdaRewriter lambdaRewriter;
  private final Inliner inliner;
  private final Predicate<DexClass> isClassEligible;
  private final Predicate<DexEncodedMethod> isProcessedConcurrently;
  private final DexEncodedMethod method;
  private final Instruction root;

  private Value eligibleInstance;
  private DexType eligibleClass;
  private DexClass eligibleClassDefinition;
  private boolean isDesugaredLambda;

  private final Map<InvokeMethod, InliningInfo> methodCallsOnInstance
      = new IdentityHashMap<>();
  private final Map<InvokeMethod, InliningInfo> extraMethodCalls
      = new IdentityHashMap<>();
  private final List<Pair<InvokeMethod, Integer>> unusedArguments
      = new ArrayList<>();

  private int estimatedCombinedSizeForInlining = 0;

  InlineCandidateProcessor(
      DexItemFactory factory,
      AppInfoWithLiveness appInfo,
      LambdaRewriter lambdaRewriter,
      Inliner inliner,
      Predicate<DexClass> isClassEligible,
      Predicate<DexEncodedMethod> isProcessedConcurrently,
      DexEncodedMethod method,
      Instruction root) {
    this.factory = factory;
    this.lambdaRewriter = lambdaRewriter;
    this.inliner = inliner;
    this.isClassEligible = isClassEligible;
    this.method = method;
    this.root = root;
    this.appInfo = appInfo;
    this.isProcessedConcurrently = isProcessedConcurrently;
  }

  int getEstimatedCombinedSizeForInlining() {
    return estimatedCombinedSizeForInlining;
  }

  // Checks if the root instruction defines eligible value, i.e. the value
  // exists and we have a definition of its class.
  boolean isInstanceEligible() {
    eligibleInstance = root.outValue();
    if (eligibleInstance == null) {
      return false;
    }

    eligibleClass =
        root.isNewInstance() ? root.asNewInstance().clazz : root.asStaticGet().getField().type;
    eligibleClassDefinition = appInfo.definitionFor(eligibleClass);
    if (eligibleClassDefinition == null && lambdaRewriter != null) {
      // Check if the class is synthesized for a desugared lambda
      eligibleClassDefinition = lambdaRewriter.getLambdaClass(eligibleClass);
      isDesugaredLambda = eligibleClassDefinition != null;
    }
    return eligibleClassDefinition != null;
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
  boolean isClassAndUsageEligible() {
    if (!isClassEligible.test(eligibleClassDefinition)) {
      return false;
    }

    if (root.isNewInstance()) {
      // NOTE: if the eligible class does not directly extend java.lang.Object,
      // we also have to guarantee that it is initialized with initializer classified as
      // TrivialInstanceInitializer. This will be checked in areInstanceUsersEligible(...).

      // There must be no static initializer on the class itself.
      return !eligibleClassDefinition.hasClassInitializer();
    }

    assert root.isStaticGet();

    // We know that desugared lambda classes satisfy eligibility requirements.
    if (isDesugaredLambda) {
      return true;
    }

    // Checking if we can safely inline class implemented following singleton-like
    // pattern, by which we assume a static final field holding on to the reference
    // initialized in class constructor.
    //
    // In general we are targeting cases when the class is defined as:
    //
    //   class X {
    //     static final X F;
    //     static {
    //       F = new X();
    //     }
    //   }
    //
    // and being used as follows:
    //
    //   void foo() {
    //     f = X.F;
    //     f.bar();
    //   }
    //
    // The main difference from the similar case of class inliner with 'new-instance'
    // instruction is that in this case the instance we inline is not just leaked, but
    // is actually published via X.F field. There are several risks we need to address
    // in this case:
    //
    //    Risk: instance stored in field X.F has changed after it was initialized in
    //      class initializer
    //    Solution: we assume that final field X.F is not modified outside the class
    //      initializer. In rare cases when it is (e.g. via reflections) it should
    //      be marked with keep rules
    //
    //    Risk: instance stored in field X.F is not initialized yet
    //    Solution: not initialized instance can only be visible if X.<clinit>
    //      triggers other class initialization which references X.F. This
    //      situation should never happen if we:
    //        -- don't allow any superclasses to have static initializer,
    //        -- don't allow any subclasses,
    //        -- guarantee the class has trivial class initializer
    //           (see CodeRewriter::computeClassInitializerInfo), and
    //        -- guarantee the instance is initialized with trivial instance
    //           initializer (see CodeRewriter::computeInstanceInitializerInfo)
    //
    //    Risk: instance stored in field X.F was mutated
    //    Solution: we require that class X does not have any instance fields, and
    //      if any of its superclasses has instance fields, accessing them will make
    //      this instance not eligible for inlining. I.e. even though the instance is
    //      publicized and its state has been mutated, it will not effect the logic
    //      of class inlining
    //

    if (eligibleClassDefinition.instanceFields().length > 0) {
      return false;
    }
    if (eligibleClassDefinition.type.hasSubtypes()) {
      assert !eligibleClassDefinition.accessFlags.isFinal();
      return false;
    }

    // Singleton instance must be initialized in class constructor.
    DexEncodedMethod classInitializer = eligibleClassDefinition.getClassInitializer();
    if (classInitializer == null || isProcessedConcurrently.test(classInitializer)) {
      return false;
    }

    TrivialInitializer info =
        classInitializer.getOptimizationInfo().getTrivialInitializerInfo();
    assert info == null || info instanceof TrivialClassInitializer;
    DexField instanceField = root.asStaticGet().getField();
    // Singleton instance field must NOT be pinned.
    return info != null &&
        ((TrivialClassInitializer) info).field == instanceField &&
        !appInfo.isPinned(eligibleClassDefinition.lookupStaticField(instanceField).field);
  }

  /**
   * Checks if the inlining candidate instance users are eligible, see comment on {@link
   * ClassInliner#processMethodCode}.
   *
   * @return null if all users are eligible, or the first ineligible user.
   */
  protected InstructionOrPhi areInstanceUsersEligible(Supplier<InliningOracle> defaultOracle) {
    // No Phi users.
    if (eligibleInstance.numberOfPhiUsers() > 0) {
      return eligibleInstance.firstPhiUser(); // Not eligible.
    }

    Set<Instruction> currentUsers = eligibleInstance.uniqueUsers();
    while (!currentUsers.isEmpty()) {
      Set<Instruction> indirectUsers = new HashSet<>();
      for (Instruction user : currentUsers) {
        // Field read/write.
        if (user.isInstanceGet()
            || (user.isInstancePut() && user.asInstancePut().value() != eligibleInstance)) {
          DexField field = user.asFieldInstruction().getField();
          if (field.clazz == eligibleClass
              && eligibleClassDefinition.lookupInstanceField(field) != null) {
            // Since class inliner currently only supports classes directly extending
            // java.lang.Object, we don't need to worry about fields defined in superclasses.
            continue;
          }
          return user; // Not eligible.
        }

        if (user.isInvokeMethod()) {
          InvokeMethod invokeMethod = user.asInvokeMethod();
          DexEncodedMethod singleTarget = findSingleTarget(invokeMethod);
          if (!isEligibleSingleTarget(singleTarget)) {
            return user; // Not eligible.
          }

          // Eligible constructor call (for new instance roots only).
          if (user.isInvokeDirect()) {
            InvokeDirect invoke = user.asInvokeDirect();
            if (factory.isConstructor(invoke.getInvokedMethod())) {
              boolean isCorrespondingConstructorCall =
                  root.isNewInstance()
                      && !invoke.inValues().isEmpty()
                      && root.outValue() == invoke.inValues().get(0);
              if (isCorrespondingConstructorCall) {
                InliningInfo inliningInfo =
                    isEligibleConstructorCall(user.asInvokeDirect(), singleTarget, defaultOracle);
                if (inliningInfo != null) {
                  methodCallsOnInstance.put(user.asInvokeDirect(), inliningInfo);
                  continue;
                }
              }
            }
          }

          // Eligible virtual method call on the instance as a receiver.
          if (user.isInvokeVirtual() || user.isInvokeInterface()) {
            InvokeMethodWithReceiver invoke = user.asInvokeMethodWithReceiver();
            InliningInfo inliningInfo =
                isEligibleDirectVirtualMethodCall(invoke, singleTarget, indirectUsers);
            if (inliningInfo != null) {
              methodCallsOnInstance.put(invoke, inliningInfo);
              continue;
            }
          }

          // Eligible usage as an invocation argument.
          if (isExtraMethodCall(invokeMethod)) {
            assert !invokeMethod.isInvokeSuper();
            assert !invokeMethod.isInvokePolymorphic();
            if (isExtraMethodCallEligible(invokeMethod, singleTarget, defaultOracle)) {
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
  //  * replace unused instance usages as arguments which are never used
  //  * inline extra methods if any, collect new direct method calls
  //  * inline direct methods if any
  //  * remove superclass initializer call and field reads
  //  * remove field writes
  //  * remove root instruction
  //
  // Returns `true` if at least one method was inlined.
  boolean processInlining(IRCode code, Supplier<InliningOracle> defaultOracle) {
    replaceUsagesAsUnusedArgument(code);

    boolean anyInlinedMethods = forceInlineExtraMethodInvocations(code);
    if (anyInlinedMethods) {
      // Reset the collections.
      methodCallsOnInstance.clear();
      extraMethodCalls.clear();
      unusedArguments.clear();
      estimatedCombinedSizeForInlining = 0;

      // Repeat user analysis
      InstructionOrPhi ineligibleUser = areInstanceUsersEligible(defaultOracle);
      if (ineligibleUser != null) {
        // We introduced a user that we cannot handle in the class inliner as a result of force
        // inlining. Abort gracefully from class inlining without removing the instance.
        //
        // Alternatively we would need to collect additional information about the behavior of
        // methods (which is bad for memory), or we would need to analyze the called methods before
        // inlining them. The latter could be good solution, since we are going to build IR for the
        // methods that need to be inlined anyway.
        return true;
      }
      assert extraMethodCalls.isEmpty();
      assert unusedArguments.isEmpty();
    }

    anyInlinedMethods |= forceInlineDirectMethodInvocations(code);
    removeMiscUsages(code);
    removeFieldReads(code);
    removeFieldWrites();
    removeInstruction(root);
    return anyInlinedMethods;
  }

  private void replaceUsagesAsUnusedArgument(IRCode code) {
    for (Pair<InvokeMethod, Integer> unusedArgument : unusedArguments) {
      InvokeMethod invoke = unusedArgument.getFirst();
      BasicBlock block = invoke.getBlock();

      ConstNumber nullValue = code.createConstNull();
      nullValue.setPosition(invoke.getPosition());
      LinkedList<Instruction> instructions = block.getInstructions();
      instructions.add(instructions.indexOf(invoke), nullValue);
      nullValue.setBlock(block);

      int argIndex = unusedArgument.getSecond() + (invoke.isInvokeMethodWithReceiver() ? 1 : 0);
      invoke.replaceValue(argIndex, nullValue.outValue());
    }
    unusedArguments.clear();
  }

  private boolean forceInlineExtraMethodInvocations(IRCode code) {
    if (extraMethodCalls.isEmpty()) {
      return false;
    }
    // Inline extra methods.
    inliner.performForcedInlining(method, code, extraMethodCalls);
    return true;
  }

  private boolean forceInlineDirectMethodInvocations(IRCode code) {
    if (methodCallsOnInstance.isEmpty()) {
      return false;
    }
    inliner.performForcedInlining(method, code, methodCallsOnInstance);
    return true;
  }

  // Remove miscellaneous users before handling field reads.
  private void removeMiscUsages(IRCode code) {
    boolean needToRemoveUnreachableBlocks = false;
    for (Instruction user : eligibleInstance.uniqueUsers()) {
      // Remove the call to superclass constructor.
      if (root.isNewInstance() &&
          user.isInvokeDirect() &&
          factory.isConstructor(user.asInvokeDirect().getInvokedMethod()) &&
          user.asInvokeDirect().getInvokedMethod().holder == eligibleClassDefinition.superType) {
        removeInstruction(user);
        continue;
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
        blockToRemove.removePredecessor(block);
        assert block.exit().isGoto();
        assert block.exit().asGoto().getTarget() == newBlock;
        needToRemoveUnreachableBlocks = true;
        continue;
      }

      if (user.isInstanceGet() || user.isInstancePut()) {
        // Leave field reads/writes until next steps.
        continue;
      }

      throw new Unreachable(
          "Unexpected usage left in method `"
              + method.method.toSourceString()
              + "` after inlining: "
              + user);
    }

    if (needToRemoveUnreachableBlocks) {
      code.removeUnreachableBlocks();
    }
  }

  // Replace field reads with appropriate values, insert phis when needed.
  private void removeFieldReads(IRCode code) {
    Map<DexField, FieldValueHelper> fieldHelpers = new IdentityHashMap<>();
    for (Instruction user : eligibleInstance.uniqueUsers()) {
      if (user.isInstanceGet()) {
        // Replace a field read with appropriate value.
        replaceFieldRead(code, user.asInstanceGet(), fieldHelpers);
        continue;
      }

      if (user.isInstancePut()) {
        // Skip in this iteration since these instructions are needed to
        // properly calculate what value should field reads be replaced with.
        continue;
      }

      throw new Unreachable(
          "Unexpected usage left in method `"
              + method.method.toSourceString()
              + "` after inlining: "
              + user);
    }
  }

  private void replaceFieldRead(IRCode code,
      InstanceGet fieldRead, Map<DexField, FieldValueHelper> fieldHelpers) {
    Value value = fieldRead.outValue();
    if (value != null) {
      FieldValueHelper helper =
          fieldHelpers.computeIfAbsent(
              fieldRead.getField(), field -> new FieldValueHelper(field, code, root, appInfo));
      Value newValue = helper.getValueForFieldRead(fieldRead.getBlock(), fieldRead);
      value.replaceUsers(newValue);
      for (FieldValueHelper fieldValueHelper : fieldHelpers.values()) {
        fieldValueHelper.replaceValue(value, newValue);
      }
      assert value.numberOfAllUsers() == 0;
      new TypeAnalysis(appInfo, code.method).widening(code.method, code);
    }
    removeInstruction(fieldRead);
  }

  private void removeFieldWrites() {
    for (Instruction user : eligibleInstance.uniqueUsers()) {
      if (!user.isInstancePut()) {
        throw new Unreachable(
            "Unexpected usage left in method `"
                + method.method.toSourceString()
                + "` after field reads removed: "
                + user);
      }
      if (user.asInstancePut().getField().clazz != eligibleClass) {
        throw new Unreachable(
            "Unexpected field write left in method `"
                + method.method.toSourceString()
                + "` after field reads removed: "
                + user);
      }
      removeInstruction(user);
    }
  }

  private InliningInfo isEligibleConstructorCall(
      InvokeDirect invoke, DexEncodedMethod singleTarget, Supplier<InliningOracle> defaultOracle) {
    assert factory.isConstructor(invoke.getInvokedMethod());
    assert isEligibleSingleTarget(singleTarget);

    // Must be a constructor called on the receiver.
    if (invoke.inValues().lastIndexOf(eligibleInstance) != 0) {
      return null;
    }

    // Must be a constructor of the exact same class.
    DexMethod init = invoke.getInvokedMethod();
    if (init.holder != eligibleClass) {
      // Calling a constructor on a class that is different from the type of the instance.
      // Gracefully abort class inlining (see the test B116282409).
      return null;
    }

    // Check that the `eligibleInstance` does not escape via the constructor.
    ParameterUsage parameterUsage = singleTarget.getOptimizationInfo().getParameterUsages(0);
    if (!isEligibleParameterUsage(parameterUsage, invoke, defaultOracle)) {
      return null;
    }

    // Don't inline code w/o normal returns into block with catch handlers (b/64432527).
    if (invoke.getBlock().hasCatchHandlers()
        && singleTarget.getOptimizationInfo().neverReturnsNormally()) {
      return null;
    }

    if (isDesugaredLambda) {
      // Lambda desugaring synthesizes eligible constructors.
      markSizeForInlining(singleTarget);
      return new InliningInfo(singleTarget, eligibleClass);
    }

    // If the superclass of the initializer is NOT java.lang.Object, the super class
    // initializer being called must be classified as TrivialInstanceInitializer.
    //
    // NOTE: since we already classified the class as eligible, it does not have
    //       any class initializers in superclass chain or in superinterfaces, see
    //       details in ClassInliner::computeClassEligible(...).
    if (eligibleClassDefinition.superType != factory.objectType) {
      TrivialInitializer info = singleTarget.getOptimizationInfo().getTrivialInitializerInfo();
      if (!(info instanceof TrivialInstanceInitializer)) {
        return null;
      }
    }

    return singleTarget.getOptimizationInfo().getClassInlinerEligibility() != null
        ? new InliningInfo(singleTarget, eligibleClass)
        : null;
  }

  // An invoke is eligible for inlinining in the following cases:
  //
  // - if it does not return the receiver
  // - if there are no uses of the out value
  // - if it is a regular chaining pattern where the only users of the out value are receivers to
  //   other invocations. In that case, we should add all indirect users of the out value to ensure
  //   they can also be inlined.
  private static boolean isEligibleInvokeWithAllUsersAsReceivers(
      ClassInlinerEligibility eligibility,
      InvokeMethodWithReceiver invoke,
      Set<Instruction> indirectUsers) {
    if (!eligibility.returnsReceiver
        || invoke.outValue() == null
        || invoke.outValue().numberOfAllUsers() == 0) {
      return true;
    }
    // For CF we no longer perform the code-rewrite in CodeRewriter.rewriteMoveResult that removes
    // out values if they alias to the receiver since that naively produces a lot of popping values
    // from the stack.
    if (invoke.outValue().numberOfPhiUsers() > 0) {
      return false;
    }
    for (Instruction instruction : invoke.outValue().uniqueUsers()) {
      if (!instruction.isInvokeMethodWithReceiver()) {
        return false;
      }
      InvokeMethodWithReceiver user = instruction.asInvokeMethodWithReceiver();
      if (user.getReceiver() != invoke.outValue()) {
        return false;
      }
      int uses = 0;
      for (Value value : user.inValues()) {
        if (value == invoke.outValue()) {
          uses++;
          if (uses > 1) {
            return false;
          }
        }
      }
    }

    indirectUsers.addAll(invoke.outValue().uniqueUsers());

    return true;
  }

  private InliningInfo isEligibleDirectVirtualMethodCall(
      InvokeMethodWithReceiver invoke,
      DexEncodedMethod singleTarget,
      Set<Instruction> indirectUsers) {
    assert isEligibleSingleTarget(singleTarget);
    if (invoke.inValues().lastIndexOf(eligibleInstance) > 0) {
      return null; // Instance passed as an argument.
    }
    return isEligibleVirtualMethodCall(
        !invoke.getBlock().hasCatchHandlers(),
        invoke.getInvokedMethod(),
        singleTarget,
        eligibility -> isEligibleInvokeWithAllUsersAsReceivers(eligibility, invoke, indirectUsers));
  }

  private InliningInfo isEligibleIndirectVirtualMethodCall(DexMethod callee) {
    DexEncodedMethod singleTarget = eligibleClassDefinition.lookupVirtualMethod(callee);
    if (isEligibleSingleTarget(singleTarget)) {
      return isEligibleVirtualMethodCall(
          false, callee, singleTarget, eligibility -> !eligibility.returnsReceiver);
    }
    return null;
  }

  private InliningInfo isEligibleVirtualMethodCall(
      boolean allowMethodsWithoutNormalReturns,
      DexMethod callee,
      DexEncodedMethod singleTarget,
      Predicate<ClassInlinerEligibility> eligibilityAcceptanceCheck) {
    assert isEligibleSingleTarget(singleTarget);

    // We should not inline a method if the invocation has type interface or virtual and the
    // signature of the invocation resolves to a private or static method.
    ResolutionResult resolutionResult = appInfo.resolveMethod(callee.holder, callee);
    if (resolutionResult.hasSingleTarget()
        && !resolutionResult.asSingleTarget().isVirtualMethod()) {
      return null;
    }

    if (singleTarget == null
        || !singleTarget.isVirtualMethod()
        || isProcessedConcurrently.test(singleTarget)) {
      return null;
    }
    if (method == singleTarget) {
      return null; // Don't inline itself.
    }

    if (isDesugaredLambda) {
      // If this is the call to method of the desugared lambda, we consider only calls
      // to main lambda method eligible (for both direct and indirect calls).
      if (singleTarget.accessFlags.isBridge()) {
        return null;
      }
      markSizeForInlining(singleTarget);
      return new InliningInfo(singleTarget, eligibleClass);
    }

    OptimizationInfo optimizationInfo = singleTarget.getOptimizationInfo();

    ClassInlinerEligibility eligibility = optimizationInfo.getClassInlinerEligibility();
    if (eligibility == null) {
      return null;
    }

    // If the method returns receiver and the return value is actually
    // used in the code we need to make some additional checks.
    if (!eligibilityAcceptanceCheck.test(eligibility)) {
      return null;
    }

    // Don't inline code w/o normal returns into block with catch handlers (b/64432527).
    if (!allowMethodsWithoutNormalReturns && optimizationInfo.neverReturnsNormally()) {
      return null;
    }

    markSizeForInlining(singleTarget);
    return new InliningInfo(singleTarget, eligibleClass);
  }

  private boolean isExtraMethodCall(InvokeMethod invoke) {
    if (invoke.isInvokeDirect() && factory.isConstructor(invoke.getInvokedMethod())) {
      return false;
    }
    if (invoke.isInvokeMethodWithReceiver()
        && invoke.asInvokeMethodWithReceiver().getReceiver() == eligibleInstance) {
      return false;
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
      InvokeMethod invoke, DexEncodedMethod singleTarget, Supplier<InliningOracle> defaultOracle) {
    // Don't consider constructor invocations and super calls, since we don't want to forcibly
    // inline them.
    assert isExtraMethodCall(invoke);
    assert isEligibleSingleTarget(singleTarget);

    List<Value> arguments = Lists.newArrayList(invoke.inValues());

    // If we got here with invocation on receiver the user is ineligible.
    if (invoke.isInvokeMethodWithReceiver() && arguments.get(0) == eligibleInstance) {
      return false;
    }

    OptimizationInfo optimizationInfo = singleTarget.getOptimizationInfo();

    // Don't inline code w/o normal returns into block with catch handlers (b/64432527).
    if (invoke.getBlock().hasCatchHandlers() && optimizationInfo.neverReturnsNormally()) {
      return false;
    }

    // Go through all arguments, see if all usages of eligibleInstance are good.
    if (!isEligibleParameterUsages(invoke, arguments, singleTarget, defaultOracle)) {
      return false;
    }

    for (int argIndex = 0; argIndex < arguments.size(); argIndex++) {
      Value argument = arguments.get(argIndex);
      if (argument == eligibleInstance && optimizationInfo.getParameterUsages(argIndex).notUsed()) {
        // Reference can be removed since it's not used.
        unusedArguments.add(new Pair<>(invoke, argIndex));
      }
    }

    extraMethodCalls.put(invoke, new InliningInfo(singleTarget, null));

    // Looks good.
    markSizeForInlining(singleTarget);
    return true;
  }

  private boolean isEligibleParameterUsages(
      InvokeMethod invoke,
      List<Value> arguments,
      DexEncodedMethod singleTarget,
      Supplier<InliningOracle> defaultOracle) {
    // Go through all arguments, see if all usages of eligibleInstance are good.
    for (int argIndex = 0; argIndex < arguments.size(); argIndex++) {
      Value argument = arguments.get(argIndex);
      if (argument != eligibleInstance) {
        continue; // Nothing to worry about.
      }

      // Have parameter usage info?
      OptimizationInfo optimizationInfo = singleTarget.getOptimizationInfo();
      ParameterUsage parameterUsage = optimizationInfo.getParameterUsages(argIndex);
      if (!isEligibleParameterUsage(parameterUsage, invoke, defaultOracle)) {
        return false;
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

    if (parameterUsage.isReturned) {
      if (!(invoke.outValue() == null || invoke.outValue().numberOfAllUsers() == 0)) {
        // Used as return value which is not ignored.
        return false;
      }
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
        InliningInfo potentialInliningInfo = isEligibleIndirectVirtualMethodCall(target);
        if (potentialInliningInfo == null) {
          return false;
        }
      } else if (type == Type.DIRECT) {
        if (!isTrivialInitializer(target)) {
          // Only calls to trivial initializers are supported at this point.
          return false;
        }
      } else {
        // Static and super calls are not yet supported.
        return false;
      }

      // Check if the method is inline-able by standard inliner.
      InlineAction inlineAction = invoke.computeInlining(defaultOracle.get(), method.method.holder);
      if (inlineAction == null) {
        return false;
      }
    }
    return true;
  }

  private boolean isTrivialInitializer(DexMethod method) {
    if (method == appInfo.dexItemFactory.objectMethods.constructor) {
      return true;
    }
    DexEncodedMethod encodedMethod = appInfo.definitionFor(method);
    return encodedMethod != null
        && encodedMethod.getOptimizationInfo().getTrivialInitializerInfo() != null;
  }

  private boolean exemptFromInstructionLimit(DexEncodedMethod inlinee) {
    DexType inlineeHolder = inlinee.method.holder;
    if (isDesugaredLambda && inlineeHolder == eligibleClass) {
      return true;
    }
    if (appInfo.isPinned(inlineeHolder)) {
      return false;
    }
    DexClass inlineeClass = appInfo.definitionFor(inlineeHolder);
    assert inlineeClass != null;

    KotlinInfo kotlinInfo = inlineeClass.getKotlinInfo();
    return kotlinInfo != null &&
        kotlinInfo.isSyntheticClass() &&
        kotlinInfo.asSyntheticClass().isLambda();
  }

  private void markSizeForInlining(DexEncodedMethod inlinee) {
    if (!exemptFromInstructionLimit(inlinee)) {
      estimatedCombinedSizeForInlining += inlinee.getCode().estimatedSizeForInlining();
    }
  }

  private DexEncodedMethod findSingleTarget(InvokeMethod invoke) {
    if (isExtraMethodCall(invoke)) {
      DexType invocationContext = method.method.holder;
      return invoke.lookupSingleTarget(appInfo, invocationContext);
    }
    // We don't use computeSingleTarget(...) on invoke since it sometimes fails to
    // find the single target, while this code may be more successful since we exactly
    // know what is the actual type of the receiver.

    // Note that we also intentionally limit ourselves to methods directly defined in
    // the instance's class. This may be improved later.
    return invoke.isInvokeDirect()
        ? eligibleClassDefinition.lookupDirectMethod(invoke.getInvokedMethod())
        : eligibleClassDefinition.lookupVirtualMethod(invoke.getInvokedMethod());
  }

  private boolean isEligibleSingleTarget(DexEncodedMethod singleTarget) {
    if (singleTarget == null) {
      return false;
    }
    if (isProcessedConcurrently.test(singleTarget)) {
      return false;
    }
    if (isDesugaredLambda && !singleTarget.accessFlags.isBridge()) {
      // OK if this is the call to the main method of a desugared lambda (for both direct and
      // indirect calls).
      //
      // Note: This is needed because lambda methods are generally processed in the same batch as
      // they are class inlined, which means that the call to isInliningCandidate() below will
      // return false.
      return true;
    }
    if (!singleTarget.isInliningCandidate(method, Reason.SIMPLE, appInfo)) {
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
}
