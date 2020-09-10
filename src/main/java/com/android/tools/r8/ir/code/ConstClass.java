// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.TypeVerificationHelper;
import com.android.tools.r8.cf.code.CfConstClass;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.AccessControl;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.UnknownValue;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public class ConstClass extends ConstInstruction {

  private final DexType clazz;

  public ConstClass(Value dest, DexType clazz) {
    super(dest);
    this.clazz = clazz;
  }

  @Override
  public int opcode() {
    return Opcodes.CONST_CLASS;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  public static ConstClass copyOf(IRCode code, ConstClass original) {
    Value newValue =
        new Value(code.valueNumberGenerator.next(), original.getOutType(), original.getLocalInfo());
    return copyOf(newValue, original);
  }

  public static ConstClass copyOf(Value newValue, ConstClass original) {
    assert newValue != original.outValue();
    return new ConstClass(newValue, original.getValue());
  }

  public Value dest() {
    return outValue;
  }

  public DexType getValue() {
    return clazz;
  }

  @Override
  public void buildDex(DexBuilder builder) {
    int dest = builder.allocatedRegister(dest(), getNumber());
    builder.add(this, new com.android.tools.r8.code.ConstClass(dest, clazz));
  }

  @Override
  public int maxInValueRegister() {
    assert false : "ConstClass has no register arguments.";
    return 0;
  }

  @Override
  public int maxOutValueRegister() {
    return Constants.U8BIT_MAX;
  }

  @Override
  public String toString() {
    return super.toString() + clazz.toSourceString();
  }

  @Override
  public boolean instructionTypeCanBeCanonicalized() {
    return true;
  }

  @Override
  public boolean instructionTypeCanThrow() {
    return true;
  }

  @Override
  public boolean instructionInstanceCanThrow() {
    return true;
  }

  @Override
  public boolean instructionInstanceCanThrow(AppView<?> appView, ProgramMethod context) {
    DexType baseType = getValue().toBaseType(appView.dexItemFactory());
    if (baseType.isPrimitiveType()) {
      return false;
    }

    // Not applicable for D8.
    if (!appView.enableWholeProgramOptimizations()) {
      // Unless the type of interest is same as the context.
      if (baseType == context.getHolderType()) {
        return false;
      }
      return true;
    }

    assert appView.appInfo().hasLiveness();
    AppView<AppInfoWithLiveness> appViewWithLiveness = appView.withLiveness();

    DexClass clazz = appView.definitionFor(baseType);
    // * Check that the class and its super types are present.
    if (clazz == null || !clazz.isResolvable(appView)) {
      return true;
    }
    // * Check that the class is accessible.
    if (AccessControl.isClassAccessible(clazz, context, appViewWithLiveness).isPossiblyFalse()) {
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
  public boolean isOutConstant() {
    return true;
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    return other.isConstClass() && other.asConstClass().clazz == clazz;
  }

  @Override
  public boolean isConstClass() {
    return true;
  }

  @Override
  public ConstClass asConstClass() {
    return this;
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, ProgramMethod context) {
    return inliningConstraints.forConstClass(clazz, context.getHolder());
  }

  @Override
  public TypeElement evaluate(AppView<?> appView) {
    return TypeElement.classClassType(appView, Nullability.definitelyNotNull());
  }

  @Override
  public DexType computeVerificationType(AppView<?> appView, TypeVerificationHelper helper) {
    return appView.dexItemFactory().classType;
  }

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    helper.storeOutValue(this, it);
  }

  @Override
  public void buildCf(CfBuilder builder) {
    builder.add(new CfConstClass(clazz));
  }

  @Override
  public AbstractValue getAbstractValue(
      AppView<AppInfoWithLiveness> appView, ProgramMethod context) {
    if (!instructionMayHaveSideEffects(appView, context)) {
      return appView.abstractValueFactory().createSingleConstClassValue(clazz);
    }
    return UnknownValue.getInstance();
  }
}
