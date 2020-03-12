// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

import static com.android.tools.r8.optimize.MemberRebindingAnalysis.isTypeVisibleFromContext;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.code.DexInitClass;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.AbstractError;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis.AnalysisAssumption;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis.Query;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.google.common.collect.Sets;

public class InitClass extends Instruction {

  private final DexType clazz;

  public InitClass(Value outValue, DexType clazz) {
    super(outValue);
    assert hasOutValue();
    assert outValue.getTypeLattice().isInt();
    assert clazz.isClassType();
    this.clazz = clazz;
  }

  public DexType getClassValue() {
    return clazz;
  }

  @Override
  public boolean isInitClass() {
    return true;
  }

  @Override
  public InitClass asInitClass() {
    return this;
  }

  @Override
  public TypeLatticeElement evaluate(AppView<?> appView) {
    return TypeLatticeElement.getInt();
  }

  @Override
  public int opcode() {
    return Opcodes.INIT_CLASS;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  @Override
  public void buildDex(DexBuilder builder) {
    int dest = builder.allocatedRegister(outValue(), getNumber());
    builder.add(this, new DexInitClass(dest, clazz));
  }

  @Override
  public void buildCf(CfBuilder builder) {
    throw new Unreachable();
  }

  @Override
  public boolean definitelyTriggersClassInitialization(
      DexType clazz,
      DexType context,
      AppView<?> appView,
      Query mode,
      AnalysisAssumption assumption) {
    return ClassInitializationAnalysis.InstructionUtils.forInitClass(
        this, clazz, appView, mode, assumption);
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    return other.isInitClass() && clazz == other.asInitClass().clazz;
  }

  @Override
  public AbstractError instructionInstanceCanThrow(AppView<?> appView, DexType context) {
    if (!isTypeVisibleFromContext(appView, context, clazz)) {
      return AbstractError.top();
    }
    if (clazz.classInitializationMayHaveSideEffects(
        appView,
        // Types that are a super type of `context` are guaranteed to be initialized already.
        type -> appView.isSubtype(context, type).isTrue(),
        Sets.newIdentityHashSet())) {
      return AbstractError.top();
    }
    return AbstractError.bottom();
  }

  @Override
  public boolean instructionMayHaveSideEffects(
      AppView<?> appView, DexType context, SideEffectAssumption assumption) {
    return instructionInstanceCanThrow(appView, context).isThrowing();
  }

  @Override
  public boolean instructionMayTriggerMethodInvocation(AppView<?> appView, DexType context) {
    if (appView.enableWholeProgramOptimizations()) {
      // In R8, check if the class initialization of `clazz` or any of its ancestor types may have
      // side effects.
      return clazz.classInitializationMayHaveSideEffects(
          appView,
          // Types that are a super type of `context` are guaranteed to be initialized already.
          type -> appView.isSubtype(context, type).isTrue(),
          Sets.newIdentityHashSet());
    } else {
      // In D8, this instruction may trigger class initialization if `clazz` is different from the
      // current context.
      return clazz != context;
    }
  }

  @Override
  public boolean instructionTypeCanThrow() {
    return true;
  }

  @Override
  public int maxInValueRegister() {
    return Constants.U8BIT_MAX;
  }

  @Override
  public int maxOutValueRegister() {
    return Constants.U8BIT_MAX;
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, DexType invocationContext) {
    return inliningConstraints.forInitClass(clazz, invocationContext);
  }

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    throw new Unimplemented();
  }

  @Override
  public boolean hasInvariantOutType() {
    return true;
  }

  @Override
  public String toString() {
    return super.toString() + "; " + clazz.toSourceString();
  }
}
