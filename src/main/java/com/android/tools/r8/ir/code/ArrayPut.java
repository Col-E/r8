// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.code.CfArrayStore;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.dex.code.DexAput;
import com.android.tools.r8.dex.code.DexAputBoolean;
import com.android.tools.r8.dex.code.DexAputByte;
import com.android.tools.r8.dex.code.DexAputChar;
import com.android.tools.r8.dex.code.DexAputObject;
import com.android.tools.r8.dex.code.DexAputShort;
import com.android.tools.r8.dex.code.DexAputWide;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.ArrayTypeElement;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
import com.android.tools.r8.ir.conversion.TypeConstraintResolver;
import com.android.tools.r8.ir.optimize.DeadCodeRemover.DeadInstructionResult;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.ir.regalloc.RegisterAllocator;
import com.android.tools.r8.lightir.LirBuilder;
import java.util.Arrays;

public class ArrayPut extends ArrayAccess {

  // Input values are ordered according to the stack order of the Java bytecode astore.
  private static final int VALUE_INDEX = 2;

  private MemberType type;

  public static ArrayPut create(MemberType type, Value array, Value index, Value value) {
    ArrayPut put = new ArrayPut(type, array, index, value);
    assert put.verify();
    return put;
  }

  public static ArrayPut createWithoutVerification(
      MemberType type, Value array, Value index, Value value) {
    return new ArrayPut(type, array, index, value);
  }

  private ArrayPut(MemberType type, Value array, Value index, Value value) {
    super(null, Arrays.asList(array, index, value));
    this.type = type;
  }

  private boolean verify() {
    assert type != null;
    assert array().verifyCompatible(ValueType.OBJECT);
    assert index().verifyCompatible(ValueType.INT);
    return true;
  }

  @Override
  public int opcode() {
    return Opcodes.ARRAY_PUT;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  public Value value() {
    return inValues.get(VALUE_INDEX);
  }

  public void replacePutValue(Value newValue) {
    replaceValue(VALUE_INDEX, newValue);
  }

  @Override
  public MemberType getMemberType() {
    return type;
  }

  @Override
  public void buildDex(DexBuilder builder) {
    int value = builder.allocatedRegister(value(), getNumber());
    int array = builder.allocatedRegister(array(), getNumber());
    int index = builder.allocatedRegister(index(), getNumber());
    DexInstruction instruction;
    switch (type) {
      case INT:
      case FLOAT:
        instruction = new DexAput(value, array, index);
        break;
      case LONG:
      case DOUBLE:
        instruction = new DexAputWide(value, array, index);
        break;
      case OBJECT:
        instruction = new DexAputObject(value, array, index);
        break;
      case BOOLEAN_OR_BYTE:
        ArrayTypeElement arrayType = array().getType().asArrayType();
        if (arrayType != null && arrayType.getMemberType() == TypeElement.getBoolean()) {
          instruction = new DexAputBoolean(value, array, index);
        } else {
          assert array().getType().isDefinitelyNull()
              || arrayType.getMemberType() == TypeElement.getByte();
          instruction = new DexAputByte(value, array, index);
        }
        break;
      case CHAR:
        instruction = new DexAputChar(value, array, index);
        break;
      case SHORT:
        instruction = new DexAputShort(value, array, index);
        break;
      case INT_OR_FLOAT:
      case LONG_OR_DOUBLE:
        throw new Unreachable("Unexpected imprecise type: " + type);
      default:
        throw new Unreachable("Unexpected type: " + type);
    }
    builder.add(this, instruction);
  }

  @Override
  public int maxInValueRegister() {
    return Constants.U8BIT_MAX;
  }

  @Override
  public int maxOutValueRegister() {
    assert false : "ArrayPut instructions define no values.";
    return 0;
  }

  @Override
  public boolean instructionInstanceCanThrow(AppView<?> appView, ProgramMethod context) {
    // Check that the array is guaranteed to be non-null and that the index is within bounds.
    Value array = array().getAliasedValue();
    if (!array.isDefinedByInstructionSatisfying(Instruction::isNewArrayEmpty)) {
      return true;
    }

    NewArrayEmpty definition = array.definition.asNewArrayEmpty();
    Value sizeValue = definition.size().getAliasedValue();
    if (sizeValue.isPhi() || !sizeValue.definition.isConstNumber()) {
      return true;
    }

    Value indexValue = index().getAliasedValue();
    if (indexValue.isPhi() || !indexValue.definition.isConstNumber()) {
      return true;
    }

    long index = indexValue.definition.asConstNumber().getRawValue();
    long size = sizeValue.definition.asConstNumber().getRawValue();
    if (index < 0 || index >= size) {
      return true;
    }

    if (array.hasLocalInfo() || indexValue.hasLocalInfo() || sizeValue.hasLocalInfo()) {
      return true;
    }

    // Check for type errors.
    TypeElement arrayType = array.getType();
    TypeElement valueType = value().getType();
    if (!arrayType.isArrayType()) {
      return true;
    }
    TypeElement memberType = arrayType.asArrayType().getMemberTypeAsValueType();
    return !valueType.lessThanOrEqualUpToNullability(memberType, appView);
  }

  @Override
  public boolean instructionMayHaveSideEffects(
      AppView<?> appView, ProgramMethod context, SideEffectAssumption assumption) {
    // This modifies the array (or throws).
    return true;
  }

  @Override
  public DeadInstructionResult canBeDeadCode(AppView<?> appView, IRCode code) {
    if (!instructionInstanceCanThrow(appView, code.context())) {
      Value arrayRoot = array().getAliasedValue();
      if (arrayRoot.isDefinedByInstructionSatisfying(Instruction::isCreatingArray)) {
        return DeadInstructionResult.deadIfInValueIsDead(arrayRoot);
      }
    }
    return DeadInstructionResult.notDead();
  }

  @Override
  public boolean identicalAfterRegisterAllocation(
      Instruction other, RegisterAllocator allocator, MethodConversionOptions conversionOptions) {
    // We cannot share ArrayPut instructions without knowledge of the type of the array input.
    // If multiple primitive array types flow to the same ArrayPut instruction the art verifier
    // gets confused.
    return false;
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    return other.isArrayPut() && other.asArrayPut().type == type;
  }

  @Override
  public boolean isArrayPut() {
    return true;
  }

  @Override
  public ArrayPut asArrayPut() {
    return this;
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, ProgramMethod context) {
    return inliningConstraints.forArrayPut();
  }

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    helper.loadInValues(this, it);
  }

  @Override
  public boolean hasInvariantOutType() {
    return true;
  }

  @Override
  public void buildCf(CfBuilder builder) {
    builder.add(new CfArrayStore(type), this);
  }

  @Override
  public boolean throwsNpeIfValueIsNull(Value value, AppView<?> appView, ProgramMethod context) {
    return array() == value;
  }

  @Override
  public boolean throwsOnNullInput() {
    return true;
  }

  @Override
  public Value getNonNullInput() {
    return array();
  }

  @Override
  public void constrainType(TypeConstraintResolver constraintResolver) {
    constraintResolver.constrainArrayMemberType(type, value(), array(), t -> type = t);
  }

  @Override
  public boolean instructionMayTriggerMethodInvocation(AppView<?> appView, ProgramMethod context) {
    return false;
  }

  @Override
  public ArrayAccess withMemberType(MemberType newMemberType) {
    return ArrayPut.create(newMemberType, array(), index(), value());
  }

  @Override
  public void buildLir(LirBuilder<Value, ?> builder) {
    builder.addArrayPut(getMemberType(), array(), index(), value());
  }
}
