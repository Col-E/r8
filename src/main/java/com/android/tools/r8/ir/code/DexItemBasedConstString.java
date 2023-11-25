// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.TypeVerificationHelper;
import com.android.tools.r8.cf.code.CfDexItemBasedConstString;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.optimize.DeadCodeRemover.DeadInstructionResult;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.lightir.LirBuilder;
import com.android.tools.r8.naming.dexitembasedstring.NameComputationInfo;

public class DexItemBasedConstString extends ConstInstruction {

  private final DexReference item;
  private final NameComputationInfo<?> nameComputationInfo;

  public DexItemBasedConstString(
      Value dest, DexReference item, NameComputationInfo<?> nameComputationInfo) {
    super(dest);
    this.item = item;
    this.nameComputationInfo = nameComputationInfo;
  }

  @Override
  public int opcode() {
    return Opcodes.DEX_ITEM_BASED_CONST_STRING;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  public static DexItemBasedConstString copyOf(IRCode code, DexItemBasedConstString original) {
    Value newValue = code.createValue(original.getOutType(), original.getLocalInfo());
    return copyOf(newValue, original);
  }

  public static DexItemBasedConstString copyOf(Value newValue, DexItemBasedConstString original) {
    assert newValue != original.outValue();
    return new DexItemBasedConstString(newValue, original.getItem(), original.nameComputationInfo);
  }

  public DexReference getItem() {
    return item;
  }

  public NameComputationInfo<?> getNameComputationInfo() {
    return nameComputationInfo;
  }

  @Override
  public boolean instructionTypeCanBeCanonicalized() {
    return true;
  }

  @Override
  public boolean isDexItemBasedConstString() {
    return true;
  }

  @Override
  public DexItemBasedConstString asDexItemBasedConstString() {
    return this;
  }

  @Override
  public void buildDex(DexBuilder builder) {
    int dest = builder.allocatedRegister(outValue(), getNumber());
    builder.add(
        this,
        new com.android.tools.r8.dex.code.DexItemBasedConstString(dest, item, nameComputationInfo));
  }

  @Override
  public void buildLir(LirBuilder<Value, ?> builder) {
    builder.addDexItemBasedConstString(item, nameComputationInfo);
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    return other.isDexItemBasedConstString()
        && other.asDexItemBasedConstString().item == item
        && other.asDexItemBasedConstString().nameComputationInfo.equals(nameComputationInfo);
  }

  @Override
  public int maxInValueRegister() {
    assert false : "DexItemBasedConstString has no register arguments.";
    return 0;
  }

  @Override
  public int maxOutValueRegister() {
    return Constants.U8BIT_MAX;
  }

  @Override
  public String toString() {
    return super.toString() + " \"" + item.toSourceString() + "\"";
  }

  @Override
  public boolean instructionTypeCanThrow() {
    return true;
  }

  @Override
  public boolean isOutConstant() {
    return true;
  }

  @Override
  public boolean instructionInstanceCanThrow(
      AppView<?> appView,
      ProgramMethod context,
      AbstractValueSupplier abstractValueSupplier,
      SideEffectAssumption assumption) {
    // A const-string instruction can usually throw an exception if the decoding of the string
    // fails. Since this string corresponds to a type or member name, though, decoding cannot fail.
    return false;
  }

  @Override
  public DeadInstructionResult canBeDeadCode(AppView<?> appView, IRCode code) {
    // No side-effect, such as throwing an exception, in CF.
    return DeadInstructionResult.deadIfOutValueIsDead();
  }

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    helper.storeOutValue(this, it);
  }

  @Override
  public void buildCf(CfBuilder builder) {
    builder.add(new CfDexItemBasedConstString(item, nameComputationInfo), this);
  }

  @Override
  public DexType computeVerificationType(AppView<?> appView, TypeVerificationHelper helper) {
    return appView.dexItemFactory().stringType;
  }

  @Override
  public TypeElement evaluate(AppView<?> appView) {
    return TypeElement.stringClassType(appView, Nullability.definitelyNotNull());
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, ProgramMethod context) {
    return inliningConstraints.forDexItemBasedConstString(item, context);
  }

  @Override
  public boolean instructionMayTriggerMethodInvocation(AppView<?> appView, ProgramMethod context) {
    return false;
  }

  @Override
  public AbstractValue getAbstractValue(
      AppView<?> appView, ProgramMethod context, AbstractValueSupplier abstractValueSupplier) {
    return appView
        .abstractValueFactory()
        .createSingleDexItemBasedStringValue(item, nameComputationInfo);
  }

  @Override
  void internalRegisterUse(UseRegistry<?> registry, DexClassAndMethod context) {
    if (nameComputationInfo.needsToRegisterReference()) {
      assert item.isDexType();
      registry.registerTypeReference(item.asDexType());
    }
  }
}
