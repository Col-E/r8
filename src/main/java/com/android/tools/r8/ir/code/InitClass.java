// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.code.CfInitClass;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.dex.code.DexInitClass;
import com.android.tools.r8.graph.AccessControl;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis.AnalysisAssumption;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis.Query;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.lightir.LirBuilder;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public class InitClass extends Instruction {

  private final DexType clazz;

  public InitClass(Value outValue, DexType clazz) {
    super(outValue);
    assert hasOutValue();
    assert outValue.getType().isInt();
    assert clazz.isClassType();
    this.clazz = clazz;
  }

  public static Builder builder() {
    return new Builder();
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
  public TypeElement evaluate(AppView<?> appView) {
    return TypeElement.getInt();
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
    builder.add(new CfInitClass(clazz), this);
  }

  @Override
  public void buildLir(LirBuilder<Value, ?> builder) {
    builder.addInitClass(clazz);
  }

  @Override
  public boolean definitelyTriggersClassInitialization(
      DexType clazz,
      ProgramMethod context,
      AppView<AppInfoWithLiveness> appView,
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
  public boolean instructionInstanceCanThrow(AppView<?> appView, ProgramMethod context) {
    // We only use InitClass instructions in R8.
    assert appView.enableWholeProgramOptimizations();
    AppView<AppInfoWithLiveness> appViewWithLiveness = appView.withLiveness();
    DexClass definition = appView.definitionFor(clazz);
    // * Check that the class is present.
    if (definition == null) {
      return true;
    }
    // * Check that the class is accessible.
    if (AccessControl.isClassAccessible(definition, context, appViewWithLiveness)
        .isPossiblyFalse()) {
      return true;
    }
    if (clazz.classInitializationMayHaveSideEffectsInContext(appView, context)) {
      return true;
    }
    return false;
  }

  @Override
  public boolean instructionMayHaveSideEffects(
      AppView<?> appView, ProgramMethod context, SideEffectAssumption assumption) {
    return instructionInstanceCanThrow(appView, context);
  }

  @Override
  public boolean instructionMayTriggerMethodInvocation(AppView<?> appView, ProgramMethod context) {
    if (appView.enableWholeProgramOptimizations()) {
      // In R8, check if the class initialization of `clazz` or any of its ancestor types may have
      // side effects.
      return clazz.classInitializationMayHaveSideEffectsInContext(appView, context);
    } else {
      // In D8, this instruction may trigger class initialization if `clazz` is different from the
      // current context.
      return clazz != context.getHolderType();
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
      InliningConstraints inliningConstraints, ProgramMethod context) {
    return inliningConstraints.forInitClass(clazz, context);
  }

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    helper.storeOutValue(this, it);
  }

  @Override
  public boolean hasInvariantOutType() {
    return true;
  }

  @Override
  public String toString() {
    return super.toString() + "; " + clazz.toSourceString();
  }

  @Override
  void internalRegisterUse(UseRegistry<?> registry, DexClassAndMethod context) {
    registry.registerInitClass(clazz);
  }

  public static class Builder extends BuilderBase<Builder, InitClass> {

    private DexType type;

    private Builder() {}

    public Builder setType(DexType type) {
      this.type = type;
      return this;
    }

    @Override
    public InitClass build() {
      return amend(new InitClass(outValue, type));
    }

    @Override
    public Builder self() {
      return this;
    }
  }
}
