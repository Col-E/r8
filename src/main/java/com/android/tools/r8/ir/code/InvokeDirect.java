// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import static com.android.tools.r8.optimize.MemberRebindingAnalysis.isMemberVisibleFromOriginalContext;

import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.code.InvokeDirectRange;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis.AnalysisAssumption;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis.Query;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import org.objectweb.asm.Opcodes;

public class InvokeDirect extends InvokeMethodWithReceiver {

  private final boolean itf;

  public InvokeDirect(DexMethod target, Value result, List<Value> arguments) {
    this(target, result, arguments, false);
  }

  public InvokeDirect(DexMethod target, Value result, List<Value> arguments, boolean itf) {
    super(target, result, arguments);
    this.itf = itf;
    // invoke-direct <init> should have no out value.
    assert !target.name.toString().equals(Constants.INSTANCE_INITIALIZER_NAME)
        || result == null;
  }

  @Override
  public Type getType() {
    return Type.DIRECT;
  }

  @Override
  protected String getTypeString() {
    return "Direct";
  }

  @Override
  public void buildDex(DexBuilder builder) {
    com.android.tools.r8.code.Instruction instruction;
    int argumentRegisters = requiredArgumentRegisters();
    builder.requestOutgoingRegisters(argumentRegisters);
    if (needsRangedInvoke(builder)) {
      assert argumentsConsecutive(builder);
      int firstRegister = argumentRegisterValue(0, builder);
      instruction = new InvokeDirectRange(firstRegister, argumentRegisters, getInvokedMethod());
    } else {
      int[] individualArgumentRegisters = new int[5];
      int argumentRegistersCount = fillArgumentRegisters(builder, individualArgumentRegisters);
      instruction = new com.android.tools.r8.code.InvokeDirect(
          argumentRegistersCount,
          getInvokedMethod(),
          individualArgumentRegisters[0],  // C
          individualArgumentRegisters[1],  // D
          individualArgumentRegisters[2],  // E
          individualArgumentRegisters[3],  // F
          individualArgumentRegisters[4]); // G
    }
    addInvokeAndMoveResult(instruction, builder);
  }

  /**
   * Two invokes of a constructor are only allowed to be considered equal if the object
   * they are initializing is the same. Art rejects code that has objects created by
   * different new-instance instructions flow to one constructor invoke.
   */
  public boolean sameConstructorReceiverValue(Invoke other) {
    if (!getInvokedMethod().name.toString().equals(Constants.INSTANCE_INITIALIZER_NAME)) {
      return true;
    }
    return inValues.get(0) == other.inValues.get(0);
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    return other.isInvokeDirect() && super.identicalNonValueNonPositionParts(other);
  }

  @Override
  public boolean isInvokeDirect() {
    return true;
  }

  @Override
  public InvokeDirect asInvokeDirect() {
    return this;
  }

  @Override
  public DexEncodedMethod lookupSingleTarget(AppInfoWithLiveness appInfo,
      DexType invocationContext) {
    return appInfo.lookupDirectTarget(getInvokedMethod());
  }

  @Override
  public Collection<DexEncodedMethod> lookupTargets(AppInfoWithSubtyping appInfo,
      DexType invocationContext) {
    DexEncodedMethod target = appInfo.lookupDirectTarget(getInvokedMethod());
    return target == null ? Collections.emptyList() : Collections.singletonList(target);
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, DexType invocationContext) {
    return inliningConstraints.forInvokeDirect(getInvokedMethod(), invocationContext);
  }

  @Override
  public void buildCf(CfBuilder builder) {
    builder.add(new CfInvoke(Opcodes.INVOKESPECIAL, getInvokedMethod(), itf));
  }

  @Override
  public boolean definitelyTriggersClassInitialization(
      DexType clazz,
      AppView<? extends AppInfo> appView,
      Query mode,
      AnalysisAssumption assumption) {
    return ClassInitializationAnalysis.InstructionUtils.forInvokeDirect(
        this, clazz, appView, mode, assumption);
  }

  @Override
  public boolean instructionMayHaveSideEffects(
      AppView<? extends AppInfo> appView, DexType context) {
    if (!appView.enableWholeProgramOptimizations()) {
      return true;
    }

    if (appView.options().debug) {
      return true;
    }

    // Check if it could throw a NullPointerException as a result of the receiver being null.
    Value receiver = getReceiver();
    if (receiver.getTypeLattice().nullability().isNullable()) {
      return true;
    }

    // Find the target and check if the invoke may have side effects.
    if (appView.appInfo().hasLiveness()) {
      AppInfoWithLiveness appInfoWithLiveness = appView.appInfo().withLiveness();
      DexEncodedMethod target = lookupSingleTarget(appInfoWithLiveness, context);
      if (target == null) {
        return true;
      }

      // Verify that the target method is accessible in the current context.
      if (!isMemberVisibleFromOriginalContext(
          appView, context, target.method.holder, target.accessFlags)) {
        return true;
      }

      // Verify that the target method does not have side-effects. For program methods, we use
      // optimization info, and for library methods, we use modeling.
      DexClass clazz = appView.definitionFor(target.method.holder);
      if (clazz == null) {
        assert false : "Expected to be able to find the enclosing class of a method definition";
        return true;
      }

      boolean targetMayHaveSideEffects;
      if (clazz.isProgramClass()) {
        targetMayHaveSideEffects =
            target.getOptimizationInfo().mayHaveSideEffects()
                && !appInfoWithLiveness.noSideEffects.containsKey(target.method);
      } else {
        targetMayHaveSideEffects =
            !appView.dexItemFactory().libraryMethodsWithoutSideEffects.contains(target.method);
      }

      if (targetMayHaveSideEffects) {
        return true;
      }

      // Success, the instruction does not have any side effects.
      return false;
    }

    return true;
  }

  @Override
  public boolean canBeDeadCode(AppView<? extends AppInfo> appView, IRCode code) {
    DexEncodedMethod method = code.method;
    if (instructionMayHaveSideEffects(appView, method.method.holder)) {
      return false;
    }

    if (appView.dexItemFactory().isConstructor(getInvokedMethod())) {
      // If it is a constructor call that initializes an uninitialized object, then the
      // uninitialized object must be dead. This is the case if all the constructor calls cannot
      // have side effects and the instance is dead except for the constructor calls.
      List<Instruction> otherInitCalls = null;
      for (Instruction user : getReceiver().uniqueUsers()) {
        if (user == this) {
          continue;
        }
        if (user.isInvokeDirect()) {
          InvokeDirect invoke = user.asInvokeDirect();
          if (appView.dexItemFactory().isConstructor(invoke.getInvokedMethod())
              && invoke.getReceiver() == getReceiver()) {
            // If another constructor call than `this` is found, then it must not have side effects.
            if (invoke.instructionMayHaveSideEffects(appView, method.method.holder)) {
              return false;
            }
            if (otherInitCalls == null) {
              otherInitCalls = new ArrayList<>();
            }
            otherInitCalls.add(invoke);
          }
        }
      }

      // Now check that the instance is dead except for the constructor calls.
      final List<Instruction> finalOtherInitCalls = otherInitCalls;
      Predicate<Instruction> ignoreConstructorCalls =
          instruction ->
              instruction == this
                  || (finalOtherInitCalls != null && finalOtherInitCalls.contains(instruction));
      if (!getReceiver().isDead(appView, code, ignoreConstructorCalls)) {
        return false;
      }

      // Verify that it is not a super-constructor call (these cannot be removed).
      if (getReceiver() == code.getThis()) {
        return false;
      }
    }

    return true;
  }
}
