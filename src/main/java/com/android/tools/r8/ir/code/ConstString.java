// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.TypeVerificationHelper;
import com.android.tools.r8.cf.code.CfConstString;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.dex.code.DexConstString;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.VerifyTypesHelper;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.UnknownValue;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.optimize.DeadCodeRemover.DeadInstructionResult;
import com.android.tools.r8.lightir.LirBuilder;
import java.io.UTFDataFormatException;

public class ConstString extends ConstInstruction {

  private final DexString value;

  public ConstString(Value dest, DexString value) {
    super(dest);
    this.value = value;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public int opcode() {
    return Opcodes.CONST_STRING;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  public static ConstString copyOf(IRCode code, ConstString original) {
    Value newValue =
        new Value(code.valueNumberGenerator.next(), original.getOutType(), original.getLocalInfo());
    return copyOf(newValue, original);
  }

  public static ConstString copyOf(Value newValue, ConstString original) {
    assert newValue != original.outValue();
    return new ConstString(newValue, original.getValue());
  }

  public Value dest() {
    return outValue;
  }

  public DexString getValue() {
    return value;
  }

  @Override
  public boolean instructionTypeCanBeCanonicalized() {
    return true;
  }

  @Override
  public void buildDex(DexBuilder builder) {
    int dest = builder.allocatedRegister(dest(), getNumber());
    builder.add(this, new DexConstString(dest, value));
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    return other.isConstString() && other.asConstString().value == value;
  }

  @Override
  public int maxInValueRegister() {
    assert false : "ConstString has no register arguments.";
    return 0;
  }

  @Override
  public int maxOutValueRegister() {
    return Constants.U8BIT_MAX;
  }

  @Override
  public String toString() {
    return super.toString() + " \"" + value + "\"";
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
  public boolean isConstString() {
    return true;
  }

  @Override
  public ConstString asConstString() {
    return this;
  }

  public boolean instructionInstanceCanThrow() {
    // The const-string instruction can be a throwing instruction in DEX, if decode() fails.
    try {
      String unused = value.toString();
    } catch (RuntimeException e) {
      if (e.getCause() instanceof UTFDataFormatException) {
        return true;
      } else {
        throw e;
      }
    }
    return false;
  }

  @Override
  public boolean instructionInstanceCanThrow(AppView<?> appView, ProgramMethod context) {
    return instructionInstanceCanThrow();
  }

  @Override
  public DeadInstructionResult canBeDeadCode(AppView<?> appView, IRCode code) {
    // No side-effect, such as throwing an exception, in CF.
    if (appView.options().isGeneratingClassFiles()
        || !instructionInstanceCanThrow(appView, code.context())) {
      return DeadInstructionResult.deadIfOutValueIsDead();
    }
    return DeadInstructionResult.notDead();
  }

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    helper.storeOutValue(this, it);
  }

  @Override
  public void buildCf(CfBuilder builder) {
    builder.add(new CfConstString(value), this);
  }

  @Override
  public DexType computeVerificationType(AppView<?> appView, TypeVerificationHelper helper) {
    return appView.dexItemFactory().stringType;
  }

  @Override
  public TypeElement evaluate(AppView<?> appView) {
    return TypeElement.stringClassType(appView, definitelyNotNull());
  }

  @Override
  public boolean instructionMayTriggerMethodInvocation(AppView<?> appView, ProgramMethod context) {
    return false;
  }

  @Override
  public AbstractValue getAbstractValue(
      AppView<? extends AppInfoWithClassHierarchy> appView, ProgramMethod context) {
    if (!instructionInstanceCanThrow(appView, context)) {
      return appView.abstractValueFactory().createSingleStringValue(value);
    }
    return UnknownValue.getInstance();
  }

  @Override
  public boolean verifyTypes(
      AppView<?> appView, ProgramMethod context, VerifyTypesHelper verifyTypesHelper) {
    assert super.verifyTypes(appView, context, verifyTypesHelper);
    TypeElement expectedType = TypeElement.stringClassType(appView, definitelyNotNull());
    assert getOutType().equals(expectedType);
    return true;
  }

  @Override
  public void buildLir(LirBuilder<Value, ?> builder) {
    builder.addConstString(value);
  }

  public static class Builder extends BuilderBase<Builder, ConstString> {

    private DexString value;

    public Builder setValue(DexString value) {
      this.value = value;
      return this;
    }

    @Override
    public ConstString build() {
      return amend(new ConstString(outValue, value));
    }

    @Override
    public Builder self() {
      return this;
    }
  }
}
