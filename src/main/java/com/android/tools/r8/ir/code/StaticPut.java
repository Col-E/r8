// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.code.CfFieldInstruction;
import com.android.tools.r8.code.Sput;
import com.android.tools.r8.code.SputBoolean;
import com.android.tools.r8.code.SputByte;
import com.android.tools.r8.code.SputChar;
import com.android.tools.r8.code.SputObject;
import com.android.tools.r8.code.SputShort;
import com.android.tools.r8.code.SputWide;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import org.objectweb.asm.Opcodes;

public class StaticPut extends FieldInstruction {

  public StaticPut(MemberType type, Value source, DexField field) {
    super(type, field, null, source);
  }

  public Value inValue() {
    assert inValues.size() == 1;
    return inValues.get(0);
  }

  @Override
  Value getFieldInOrOutValue() {
    return inValue();
  }

  @Override
  public void buildDex(DexBuilder builder) {
    com.android.tools.r8.code.Instruction instruction;
    int src = builder.allocatedRegister(inValue(), getNumber());
    DexField field = getField();
    switch (getType()) {
      case INT:
      case FLOAT:
        instruction = new Sput(src, field);
        break;
      case LONG:
      case DOUBLE:
        instruction = new SputWide(src, field);
        break;
      case OBJECT:
        instruction = new SputObject(src, field);
        break;
      case BOOLEAN:
        instruction = new SputBoolean(src, field);
        break;
      case BYTE:
        instruction = new SputByte(src, field);
        break;
      case CHAR:
        instruction = new SputChar(src, field);
        break;
      case SHORT:
        instruction = new SputShort(src, field);
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
    // This can cause <clinit> to run.
    return true;
  }

  @Override
  public int maxInValueRegister() {
    return Constants.U8BIT_MAX;
  }

  @Override
  public int maxOutValueRegister() {
    assert false : "StaticPut instructions define no values.";
    return 0;
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    if (!other.isStaticPut()) {
      return false;
    }
    StaticPut o = other.asStaticPut();
    return o.getField() == getField() && o.getType() == getType();
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, DexType invocationContext) {
    return inliningConstraints.forStaticPut(getField(), invocationContext);
  }

  @Override
  public String toString() {
    return super.toString() + "; field: " + getField().toSourceString();
  }

  @Override
  public boolean isStaticPut() {
    return true;
  }

  @Override
  public StaticPut asStaticPut() {
    return this;
  }

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    helper.loadInValues(this, it);
  }

  @Override
  public void buildCf(CfBuilder builder) {
    builder.add(
        new CfFieldInstruction(Opcodes.PUTSTATIC, getField(), builder.resolveField(getField())));
  }

  @Override
  public boolean triggersInitializationOfClass(DexType klass) {
    return getField().clazz == klass;
  }
}
