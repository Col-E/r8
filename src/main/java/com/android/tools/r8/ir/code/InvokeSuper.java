// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.code.InvokeSuperRange;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.TypeEnvironment;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.optimize.Inliner.Constraint;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.objectweb.asm.Opcodes;

public class InvokeSuper extends InvokeMethodWithReceiver {

  public final boolean itf;

  public InvokeSuper(DexMethod target, Value result, List<Value> arguments, boolean itf) {
    super(target, result, arguments);
    this.itf = itf;
  }

  @Override
  public Type getType() {
    return Type.SUPER;
  }

  @Override
  protected String getTypeString() {
    return "Super";
  }

  @Override
  public DexEncodedMethod computeSingleTarget(
      AppInfoWithLiveness appInfo, TypeEnvironment typeEnvironment, DexType invocationContext) {
    if (invocationContext == null) {
      return null;
    }
    try {
      return appInfo.lookupSuperTarget(getInvokedMethod(), invocationContext);
    } catch (CompilationError ce) {
      // In case of illegal invoke-super, ignore inlining/optimizing it by returning null here.
      return null;
    }
  }

  @Override
  public void buildDex(DexBuilder builder) {
    com.android.tools.r8.code.Instruction instruction;
    int argumentRegisters = requiredArgumentRegisters();
    builder.requestOutgoingRegisters(argumentRegisters);
    if (needsRangedInvoke(builder)) {
      assert argumentsConsecutive(builder);
      int firstRegister = argumentRegisterValue(0, builder);
      instruction = new InvokeSuperRange(firstRegister, argumentRegisters, getInvokedMethod());
    } else {
      int[] individualArgumentRegisters = new int[5];
      int argumentRegistersCount = fillArgumentRegisters(builder, individualArgumentRegisters);
      instruction = new com.android.tools.r8.code.InvokeSuper(
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

  @Override
  public void buildCf(CfBuilder builder) {
    builder.add(new CfInvoke(Opcodes.INVOKESPECIAL, getInvokedMethod(), itf));
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    return other.isInvokeSuper() && super.identicalNonValueNonPositionParts(other);
  }

  @Override
  public boolean isInvokeSuper() {
    return true;
  }

  @Override
  public InvokeSuper asInvokeSuper() {
    return this;
  }

  @Override
  public DexEncodedMethod lookupSingleTarget(AppInfoWithLiveness appInfo,
      DexType invocationContext) {
    return appInfo.lookupSuperTarget(getInvokedMethod(), invocationContext);
  }

  @Override
  public Collection<DexEncodedMethod> lookupTargets(AppInfoWithSubtyping appInfo,
      DexType invocationContext) {
    DexEncodedMethod target = appInfo.lookupSuperTarget(getInvokedMethod(), invocationContext);
    return target == null ? Collections.emptyList() : Collections.singletonList(target);
  }

  @Override
  public Constraint inliningConstraint(
      InliningConstraints inliningConstraints, DexType invocationContext) {
    return inliningConstraints.forInvokeSuper(getInvokedMethod(), invocationContext);
  }
}
