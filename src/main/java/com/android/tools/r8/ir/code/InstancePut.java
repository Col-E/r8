// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.code.CfFieldInstruction;
import com.android.tools.r8.code.Iput;
import com.android.tools.r8.code.IputBoolean;
import com.android.tools.r8.code.IputByte;
import com.android.tools.r8.code.IputChar;
import com.android.tools.r8.code.IputObject;
import com.android.tools.r8.code.IputShort;
import com.android.tools.r8.code.IputWide;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import java.util.Arrays;
import org.objectweb.asm.Opcodes;

public class InstancePut extends FieldInstruction {

  public InstancePut(MemberType type, DexField field, Value object, Value value) {
    super(type, field, null, Arrays.asList(object, value));
    assert object().type.isObjectOrNull();
    assert value().type.compatible(ValueType.fromDexType(field.type));
  }

  public Value object() {
    return inValues.get(0);
  }

  public Value value() {
    return inValues.get(1);
  }

  @Override
  public void buildDex(DexBuilder builder) {
    com.android.tools.r8.code.Instruction instruction;
    int valueRegister = builder.allocatedRegister(value(), getNumber());
    int objectRegister = builder.allocatedRegister(object(), getNumber());
    switch (type) {
      case INT:
      case FLOAT:
      case INT_OR_FLOAT:
        instruction = new Iput(valueRegister, objectRegister, field);
        break;
      case LONG:
      case DOUBLE:
      case LONG_OR_DOUBLE:
        instruction = new IputWide(valueRegister, objectRegister, field);
        break;
      case OBJECT:
        instruction = new IputObject(valueRegister, objectRegister, field);
        break;
      case BOOLEAN:
        instruction = new IputBoolean(valueRegister, objectRegister, field);
        break;
      case BYTE:
        instruction = new IputByte(valueRegister, objectRegister, field);
        break;
      case CHAR:
        instruction = new IputChar(valueRegister, objectRegister, field);
        break;
      case SHORT:
        instruction = new IputShort(valueRegister, objectRegister, field);
        break;
      default:
        throw new Unreachable("Unexpected type " + type);
    }
    builder.add(this, instruction);
  }

  @Override
  public boolean instructionTypeCanThrow() {
    return true;
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    InstancePut o = other.asInstancePut();
    return o.field == field && o.type == type;
  }

  @Override
  public int compareNonValueParts(Instruction other) {
    InstancePut o = other.asInstancePut();
    int result = field.slowCompareTo(o.field);
    if (result != 0) {
      return result;
    }
    return type.ordinal() - o.type.ordinal();
  }

  @Override
  public int maxInValueRegister() {
    return Constants.U4BIT_MAX;
  }

  @Override
  public int maxOutValueRegister() {
    assert false : "InstancePut instructions define no values.";
    return 0;
  }

  @Override
  DexEncodedField lookupTarget(DexType type, AppInfo appInfo) {
    return appInfo.lookupInstanceTarget(type, field);
  }

  @Override
  public boolean isInstancePut() {
    return true;
  }

  @Override
  public InstancePut asInstancePut() {
    return this;
  }

  @Override
  public String toString() {
    return super.toString() + "; field: " + field.toSourceString();
  }

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    helper.loadInValues(this, it);
  }

  @Override
  public void buildCf(CfBuilder builder) {
    builder.add(new CfFieldInstruction(Opcodes.PUTFIELD, field, builder.resolveField(field)));
  }

  @Override
  public boolean throwsNpeIfValueIsNull(Value value) {
    return object() == value;
  }
}
