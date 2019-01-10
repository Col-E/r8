// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.TypeVerificationHelper;
import com.android.tools.r8.cf.code.CfFieldInstruction;
import com.android.tools.r8.code.Iget;
import com.android.tools.r8.code.IgetBoolean;
import com.android.tools.r8.code.IgetByte;
import com.android.tools.r8.code.IgetChar;
import com.android.tools.r8.code.IgetObject;
import com.android.tools.r8.code.IgetShort;
import com.android.tools.r8.code.IgetWide;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import org.objectweb.asm.Opcodes;

public class InstanceGet extends FieldInstruction {

  public InstanceGet(Value dest, Value object, DexField field) {
    super(field, dest, object);
  }

  public Value dest() {
    return outValue;
  }

  public Value object() {
    assert inValues.size() == 1;
    return inValues.get(0);
  }

  @Override
  public boolean couldIntroduceAnAlias() {
    return true;
  }

  @Override
  public void buildDex(DexBuilder builder) {
    int destRegister = builder.allocatedRegister(dest(), getNumber());
    int objectRegister = builder.allocatedRegister(object(), getNumber());
    com.android.tools.r8.code.Instruction instruction;
    DexField field = getField();
    switch (getType()) {
      case INT:
      case FLOAT:
        instruction = new Iget(destRegister, objectRegister, field);
        break;
      case LONG:
      case DOUBLE:
        instruction = new IgetWide(destRegister, objectRegister, field);
        break;
      case OBJECT:
        instruction = new IgetObject(destRegister, objectRegister, field);
        break;
      case BOOLEAN:
        instruction = new IgetBoolean(destRegister, objectRegister, field);
        break;
      case BYTE:
        instruction = new IgetByte(destRegister, objectRegister, field);
        break;
      case CHAR:
        instruction = new IgetChar(destRegister, objectRegister, field);
        break;
      case SHORT:
        instruction = new IgetShort(destRegister, objectRegister, field);
        break;
      case INT_OR_FLOAT:
      case LONG_OR_DOUBLE:
        throw new Unreachable("Unexpected imprecise type: " + getType());
      default:
        throw new Unreachable("Unexpected type: " + getType());
    }
    builder.add(this, instruction);
  }

  @Override
  public boolean instructionTypeCanThrow() {
    return true;
  }

  @Override
  public int maxInValueRegister() {
    return Constants.U4BIT_MAX;
  }

  @Override
  public int maxOutValueRegister() {
    return Constants.U4BIT_MAX;
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    if (!other.isInstanceGet()) {
      return false;
    }
    InstanceGet o = other.asInstanceGet();
    return o.getField() == getField() && o.getType() == getType();
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, DexType invocationContext) {
    return inliningConstraints.forInstanceGet(getField(), invocationContext);
  }

  @Override
  public boolean isInstanceGet() {
    return true;
  }

  @Override
  public InstanceGet asInstanceGet() {
    return this;
  }

  @Override
  public String toString() {
    return super.toString() + "; field: " + getField().toSourceString();
  }

  @Override
  public TypeLatticeElement evaluate(AppInfo appInfo) {
    return TypeLatticeElement.fromDexType(getField().type, Nullability.maybeNull(), appInfo);
  }

  @Override
  public DexType computeVerificationType(TypeVerificationHelper helper) {
    return getField().type;
  }

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    helper.loadInValues(this, it);
    helper.storeOutValue(this, it);
  }

  @Override
  public void buildCf(CfBuilder builder) {
    builder.add(
        new CfFieldInstruction(Opcodes.GETFIELD, getField(), builder.resolveField(getField())));
  }

  @Override
  public boolean throwsNpeIfValueIsNull(Value value, DexItemFactory dexItemFactory) {
    return object() == value;
  }
}
