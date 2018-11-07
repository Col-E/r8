// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.TypeVerificationHelper;
import com.android.tools.r8.cf.code.CfArrayLoad;
import com.android.tools.r8.code.Aget;
import com.android.tools.r8.code.AgetBoolean;
import com.android.tools.r8.code.AgetByte;
import com.android.tools.r8.code.AgetChar;
import com.android.tools.r8.code.AgetObject;
import com.android.tools.r8.code.AgetShort;
import com.android.tools.r8.code.AgetWide;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.ir.regalloc.RegisterAllocator;
import java.util.Arrays;

public class ArrayGet extends Instruction {

  private final MemberType type;

  public ArrayGet(MemberType type, Value dest, Value array, Value index) {
    super(dest, Arrays.asList(array, index));
    this.type = type;
  }

  public Value dest() {
    return outValue;
  }

  public Value array() {
    return inValues.get(0);
  }

  public Value index() {
    return inValues.get(1);
  }

  @Override
  public void buildDex(DexBuilder builder) {
    int dest = builder.allocatedRegister(dest(), getNumber());
    int array = builder.allocatedRegister(array(), getNumber());
    int index = builder.allocatedRegister(index(), getNumber());
    com.android.tools.r8.code.Instruction instruction;
    switch (type) {
      case INT:
      case FLOAT:
      case INT_OR_FLOAT:
        instruction = new Aget(dest, array, index);
        break;
      case LONG:
      case DOUBLE:
      case LONG_OR_DOUBLE:
        assert builder.getOptions().canUseSameArrayAndResultRegisterInArrayGetWide()
            || dest != array;
        instruction = new AgetWide(dest, array, index);
        break;
      case OBJECT:
        instruction = new AgetObject(dest, array, index);
        break;
      case BOOLEAN:
        instruction = new AgetBoolean(dest, array, index);
        break;
      case BYTE:
        instruction = new AgetByte(dest, array, index);
        break;
      case CHAR:
        instruction = new AgetChar(dest, array, index);
        break;
      case SHORT:
        instruction = new AgetShort(dest, array, index);
        break;
      default:
        throw new Unreachable("Unexpected type " + type);
    }
    builder.add(this, instruction);
  }

  @Override
  public boolean identicalAfterRegisterAllocation(Instruction other, RegisterAllocator allocator) {
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
  public boolean instructionTypeCanThrow() {
    // TODO: Determine if the array index out-of-bounds exception cannot happen.
    return true;
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
      InliningConstraints inliningConstraints, DexType invocationContext) {
    return inliningConstraints.forArrayGet();
  }

  @Override
  public boolean hasInvariantOutType() {
    return false;
  }

  @Override
  public DexType computeVerificationType(TypeVerificationHelper helper) {
    // This method is not called for ArrayGet on primitive array.
    assert this.outValue.type.isObject();
    DexType arrayType = helper.getDexType(array());
    if (arrayType == DexItemFactory.nullValueType) {
      // JVM 8 §4.10.1.9.aaload: Array component type of null is null.
      return arrayType;
    }
    return arrayType.toArrayElementType(helper.getFactory());
  }

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    helper.loadInValues(this, it);
    helper.storeOutValue(this, it);
  }

  @Override
  public void buildCf(CfBuilder builder) {
    builder.add(new CfArrayLoad(type));
  }

  @Override
  public TypeLatticeElement evaluate(AppInfo appInfo) {
    return array().getTypeLattice().arrayGet(appInfo);
  }

  @Override
  public boolean throwsNpeIfValueIsNull(Value value, DexItemFactory dexItemFactory) {
    return array() == value;
  }
}
