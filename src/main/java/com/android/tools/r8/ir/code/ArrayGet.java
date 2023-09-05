// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.TypeVerificationHelper;
import com.android.tools.r8.cf.code.CfArrayLoad;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.dex.code.DexAget;
import com.android.tools.r8.dex.code.DexAgetBoolean;
import com.android.tools.r8.dex.code.DexAgetByte;
import com.android.tools.r8.dex.code.DexAgetChar;
import com.android.tools.r8.dex.code.DexAgetObject;
import com.android.tools.r8.dex.code.DexAgetShort;
import com.android.tools.r8.dex.code.DexAgetWide;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.ArrayTypeElement;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
import com.android.tools.r8.ir.conversion.TypeConstraintResolver;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.ir.regalloc.RegisterAllocator;
import com.android.tools.r8.lightir.LirBuilder;
import java.util.Arrays;
import java.util.Set;

public class ArrayGet extends ArrayAccess {

  private MemberType type;

  public ArrayGet(MemberType type, Value dest, Value array, Value index) {
    super(dest, Arrays.asList(array, index));
    this.type = type;
  }

  @Override
  public int opcode() {
    return Opcodes.ARRAY_GET;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  public Value dest() {
    return outValue;
  }

  @Override
  public MemberType getMemberType() {
    return type;
  }

  @Override
  public boolean couldIntroduceAnAlias(AppView<?> appView, Value root) {
    assert root != null && root.getType().isReferenceType();
    assert outValue != null;
    return outValue.getType().isReferenceType();
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public void buildDex(DexBuilder builder) {
    int dest = builder.allocatedRegister(dest(), getNumber());
    int array = builder.allocatedRegister(array(), getNumber());
    int index = builder.allocatedRegister(index(), getNumber());
    DexInstruction instruction;
    switch (type) {
      case INT:
      case FLOAT:
        instruction = new DexAget(dest, array, index);
        break;
      case LONG:
      case DOUBLE:
        assert builder.getOptions().canUseSameArrayAndResultRegisterInArrayGetWide()
            || dest != array;
        instruction = new DexAgetWide(dest, array, index);
        break;
      case OBJECT:
        instruction = new DexAgetObject(dest, array, index);
        break;
      case BOOLEAN_OR_BYTE:
        ArrayTypeElement arrayType = array().getType().asArrayType();
        if (arrayType != null && arrayType.getMemberType() == TypeElement.getBoolean()) {
          instruction = new DexAgetBoolean(dest, array, index);
        } else {
          assert array().getType().isDefinitelyNull()
              || arrayType.getMemberType() == TypeElement.getByte();
          instruction = new DexAgetByte(dest, array, index);
        }
        break;
      case CHAR:
        instruction = new DexAgetChar(dest, array, index);
        break;
      case SHORT:
        instruction = new DexAgetShort(dest, array, index);
        break;
      case INT_OR_FLOAT:
      case LONG_OR_DOUBLE:
        throw new Unreachable("Unexpected imprecise type: " + type);
      default:
        throw new Unreachable("Unexpected type " + type);
    }
    builder.add(this, instruction);
  }

  @Override
  public boolean identicalAfterRegisterAllocation(
      Instruction other, RegisterAllocator allocator, MethodConversionOptions conversionOptions) {
    // We cannot share ArrayGet instructions without knowledge of the type of the array input.
    // If multiple primitive array types flow to the same ArrayGet instruction the art verifier
    // gets confused.
    return false;
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    return other.isArrayGet() && other.asArrayGet().type == type;
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
  public boolean isArrayGet() {
    return true;
  }

  @Override
  public ArrayGet asArrayGet() {
    return this;
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, ProgramMethod context) {
    return inliningConstraints.forArrayGet();
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public boolean hasInvariantOutType() {
    return false;
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public DexType computeVerificationType(AppView<?> appView, TypeVerificationHelper helper) {
    // This method is not called for ArrayGet on primitive array.
    assert this.outValue.getType().isReferenceType();
    DexType arrayType = helper.getDexType(array());
    if (arrayType == DexItemFactory.nullValueType) {
      // JVM 8 §4.10.1.9.aaload: Array component type of null is null.
      return arrayType;
    }
    return arrayType.toArrayElementType(appView.dexItemFactory());
  }

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    helper.loadInValues(this, it);
    helper.storeOutValue(this, it);
  }

  @Override
  public void buildCf(CfBuilder builder) {
    builder.add(new CfArrayLoad(type), this);
  }

  @Override
  public TypeElement evaluate(AppView<?> appView) {
    ArrayTypeElement arrayTypeLattice =
        array().getType().isArrayType() ? array().getType().asArrayType() : null;
    switch (getMemberType()) {
      case OBJECT:
        // If the out-type of the array is bottom (the input array must be definitely null), then
        // the instruction cannot return. For now we return NULL as the type to ensure we have a
        // type consistent witness for the out-value type. We could consider returning bottom in
        // this case as the value is indeed empty, i.e., the instruction will always fail.
        TypeElement valueType =
            arrayTypeLattice == null
                ? TypeElement.getNull()
                : arrayTypeLattice.getMemberTypeAsValueType();
        assert valueType.isReferenceType();
        return valueType;
      case BOOLEAN_OR_BYTE:
      case CHAR:
      case SHORT:
      case INT:
        assert arrayTypeLattice == null || arrayTypeLattice.getMemberTypeAsValueType().isInt();
        return TypeElement.getInt();
      case FLOAT:
        assert arrayTypeLattice == null || arrayTypeLattice.getMemberTypeAsValueType().isFloat();
        return TypeElement.getFloat();
      case LONG:
        assert arrayTypeLattice == null || arrayTypeLattice.getMemberTypeAsValueType().isLong();
        return TypeElement.getLong();
      case DOUBLE:
        assert arrayTypeLattice == null || arrayTypeLattice.getMemberTypeAsValueType().isDouble();
        return TypeElement.getDouble();
      case INT_OR_FLOAT:
        assert arrayTypeLattice == null
            || arrayTypeLattice.getMemberTypeAsValueType().isSinglePrimitive();
        return checkConstraint(dest(), ValueTypeConstraint.INT_OR_FLOAT);
      case LONG_OR_DOUBLE:
        assert arrayTypeLattice == null
            || arrayTypeLattice.getMemberTypeAsValueType().isWidePrimitive();
        return checkConstraint(dest(), ValueTypeConstraint.LONG_OR_DOUBLE);
      default:
        throw new Unreachable("Unexpected member type: " + getMemberType());
    }
  }

  private static TypeElement checkConstraint(Value value, ValueTypeConstraint constraint) {
    TypeElement latticeElement = value.constrainedType(constraint);
    if (latticeElement != null) {
      return latticeElement;
    }
    throw new CompilationError(
        "Failure to constrain value: " + value + " by constraint: " + constraint);
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
  @SuppressWarnings("ReferenceEquality")
  public boolean outTypeKnownToBeBoolean(Set<Phi> seen) {
    return array().getType().isArrayType()
        && array().getType().asArrayType().getMemberType() == TypeElement.getBoolean();
  }

  @Override
  public void constrainType(TypeConstraintResolver constraintResolver) {
    constraintResolver.constrainArrayMemberType(type, dest(), array(), t -> type = t);
  }

  @Override
  public boolean instructionMayTriggerMethodInvocation(AppView<?> appView, ProgramMethod context) {
    return false;
  }

  @Override
  public ArrayAccess withMemberType(MemberType newMemberType) {
    return new ArrayGet(newMemberType, outValue(), array(), index());
  }

  @Override
  public boolean instructionMayHaveSideEffects(
      AppView<?> appView,
      ProgramMethod context,
      AbstractValueSupplier abstractValueSupplier,
      SideEffectAssumption assumption) {
    // TODO(b/203731608): Move to instructionInstanceCanThrow and remove the method.
    if (array().isPhi() || !index().isConstant()) {
      return true;
    }
    AbstractValue abstractValue = array().getAliasedValue().getAbstractValue(appView, context);
    if (!abstractValue.hasKnownArrayLength()) {
      return true;
    }
    int newArraySize = abstractValue.getKnownArrayLength();
    int index = index().getConstInstruction().asConstNumber().getIntValue();
    return newArraySize <= 0 || index < 0 || newArraySize <= index;
  }

  @Override
  public void buildLir(LirBuilder<Value, ?> builder) {
    builder.addArrayGet(getMemberType(), array(), index());
  }
}
