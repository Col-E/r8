// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.TypeVerificationHelper;
import com.android.tools.r8.cf.code.CfNewUnboxedEnum;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.dex.code.DexNewUnboxedEnumInstance;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.analysis.VerifyTypesHelper;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.ir.optimize.enums.EnumUnboxer;
import com.android.tools.r8.ir.optimize.enums.EnumUnboxerImpl;
import com.android.tools.r8.lightir.LirBuilder;

/**
 * Special instruction used by {@link EnumUnboxerImpl}.
 *
 * <p>When applying the enum unboxer to the application, we move the class initializer of each
 * unboxed enum to its utility class, and change each {@link NewInstance} instruction that
 * instantiates the unboxed enum into a {@link NewUnboxedEnumInstance} that holds the ordinal of the
 * enum instance.
 *
 * <p>The {@link NewUnboxedEnumInstance} is an instruction that produces an (initialized) instance
 * of the unboxed enum, i.e., the out-type is a non-nullable class type. This is important for the
 * code to type check until lens code rewriting, which replaces the {@link NewUnboxedEnumInstance}
 * instructions by {@link ConstNumber} instructions.
 *
 * <p>Note: The {@link NewUnboxedEnumInstance} is only used from {@link EnumUnboxer#unboxEnums}
 * until the execution of the {@link com.android.tools.r8.ir.conversion.PostMethodProcessor}. There
 * should be no instances of {@link NewUnboxedEnumInstance} (nor {@link CfNewUnboxedEnum}, {@link
 * DexNewUnboxedEnumInstance}) after IR processing has finished.
 */
public class NewUnboxedEnumInstance extends Instruction {

  public final DexType clazz;
  private final int ordinal;

  public NewUnboxedEnumInstance(DexType clazz, int ordinal, Value dest) {
    super(dest);
    assert clazz != null;
    this.clazz = clazz;
    this.ordinal = ordinal;
  }

  public int getOrdinal() {
    return ordinal;
  }

  public DexType getType() {
    return clazz;
  }

  @Override
  public int opcode() {
    return Opcodes.NEW_UNBOXED_ENUM_INSTANCE;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  @Override
  public void buildDex(DexBuilder builder) {
    int dest = builder.allocatedRegister(outValue(), getNumber());
    builder.add(this, new DexNewUnboxedEnumInstance(dest, clazz, ordinal));
  }

  @Override
  public String toString() {
    return super.toString() + " " + clazz;
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    return other.isNewUnboxedEnumInstance() && other.asNewUnboxedEnumInstance().clazz == clazz;
  }

  @Override
  public int maxInValueRegister() {
    assert false : "NewUnboxedEnumInstance has no register arguments";
    return 0;
  }

  @Override
  public int maxOutValueRegister() {
    return Constants.U8BIT_MAX;
  }

  @Override
  public boolean instructionTypeCanThrow() {
    // Depending on how this instruction is lowered to CF/DEX the instruction type may throw. If we
    // lower the instruction to a const-number, then it can't throw, but if we lower it to something
    // that triggers the class initialization of the enum utility class, then it could throw.
    return true;
  }

  @Override
  public boolean isNewUnboxedEnumInstance() {
    return true;
  }

  @Override
  public NewUnboxedEnumInstance asNewUnboxedEnumInstance() {
    return this;
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, ProgramMethod context) {
    return inliningConstraints.forNewUnboxedEnumInstance(clazz, context);
  }

  @Override
  public boolean hasInvariantOutType() {
    return true;
  }

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    helper.storeOutValue(this, it);
  }

  @Override
  public void buildCf(CfBuilder builder) {
    builder.add(new CfNewUnboxedEnum(clazz, ordinal), this);
  }

  @Override
  public DexType computeVerificationType(AppView<?> appView, TypeVerificationHelper helper) {
    return clazz;
  }

  @Override
  public TypeElement evaluate(AppView<?> appView) {
    return TypeElement.fromDexType(clazz, Nullability.definitelyNotNull(), appView);
  }

  @Override
  public boolean instructionMayTriggerMethodInvocation(AppView<?> appView, ProgramMethod context) {
    // Conservatively return true.
    return true;
  }

  @Override
  public boolean verifyTypes(AppView<?> appView, VerifyTypesHelper verifyTypesHelper) {
    TypeElement type = getOutType();
    assert type.isClassType();
    assert type.asClassType().getClassType() == clazz || appView.options().testing.allowTypeErrors;
    assert type.isDefinitelyNotNull();
    return true;
  }

  @Override
  void internalRegisterUse(UseRegistry<?> registry, DexClassAndMethod context) {
    registry.registerNewUnboxedEnumInstance(clazz);
  }

  @Override
  public void buildLir(LirBuilder<Value, ?> builder) {
    builder.addNewUnboxedEnumInstance(clazz, ordinal);
  }
}
