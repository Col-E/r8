// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;


import com.android.tools.r8.cf.code.CfArithmeticBinop;
import com.android.tools.r8.cf.code.CfCheckCast;
import com.android.tools.r8.cf.code.CfConstClass;
import com.android.tools.r8.cf.code.CfConstNull;
import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.cf.code.CfConstString;
import com.android.tools.r8.cf.code.CfFieldInstruction;
import com.android.tools.r8.cf.code.CfGoto;
import com.android.tools.r8.cf.code.CfIf;
import com.android.tools.r8.cf.code.CfIfCmp;
import com.android.tools.r8.cf.code.CfInstanceOf;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfInvokeDynamic;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfMonitor;
import com.android.tools.r8.cf.code.CfNew;
import com.android.tools.r8.cf.code.CfNop;
import com.android.tools.r8.cf.code.CfPosition;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.cf.code.CfReturnVoid;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfSwitch;
import com.android.tools.r8.cf.code.CfThrow;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.code.Monitor.Type;
import com.android.tools.r8.ir.code.ValueType;
import org.objectweb.asm.Opcodes;

public class CfInstructionSubject implements InstructionSubject {
  protected final CfInstruction instruction;

  public CfInstructionSubject(CfInstruction instruction) {
    this.instruction = instruction;
  }

  @Override
  public boolean isFieldAccess() {
    return instruction instanceof CfFieldInstruction;
  }

  @Override
  public boolean isInstancePut() {
    return instruction instanceof CfFieldInstruction
        && ((CfFieldInstruction) instruction).getOpcode() == Opcodes.PUTFIELD;
  }

  @Override
  public boolean isStaticPut() {
    return instruction instanceof CfFieldInstruction
        && ((CfFieldInstruction) instruction).getOpcode() == Opcodes.PUTSTATIC;
  }

  @Override
  public boolean isInstanceGet() {
    return instruction instanceof CfFieldInstruction
        && ((CfFieldInstruction) instruction).getOpcode() == Opcodes.GETFIELD;
  }

  @Override
  public boolean isStaticGet() {
    return instruction instanceof CfFieldInstruction
        && ((CfFieldInstruction) instruction).getOpcode() == Opcodes.GETSTATIC;
  }

  @Override
  public DexField getField() {
    assert isFieldAccess();
    return ((CfFieldInstruction) instruction).getField();
  }

  @Override
  public boolean isInvoke() {
    return instruction instanceof CfInvoke || instruction instanceof CfInvokeDynamic;
  }

  @Override
  public boolean isInvokeVirtual() {
    return instruction instanceof CfInvoke
        && ((CfInvoke) instruction).getOpcode() == Opcodes.INVOKEVIRTUAL;
  }

  @Override
  public boolean isInvokeInterface() {
    return instruction instanceof CfInvoke
        && ((CfInvoke) instruction).getOpcode() == Opcodes.INVOKEINTERFACE;
  }

  @Override
  public boolean isInvokeStatic() {
    return instruction instanceof CfInvoke
        && ((CfInvoke) instruction).getOpcode() == Opcodes.INVOKESTATIC;
  }

  @Override
  public DexMethod getMethod() {
    assert isInvoke();
    return ((CfInvoke) instruction).getMethod();
  }

  @Override
  public boolean isNop() {
    return instruction instanceof CfNop;
  }

  @Override
  public boolean isConstNumber() {
    return instruction instanceof CfConstNumber;
  }

  @Override
  public boolean isConstNumber(long value) {
    return instruction instanceof CfConstNumber
        && ((CfConstNumber) instruction).getRawValue() == value;
  }

  @Override
  public boolean isConstNull() {
    return instruction instanceof CfConstNull;
  }

  @Override
  public boolean isConstString(JumboStringMode jumboStringMode) {
    return instruction instanceof CfConstString;
  }

  @Override
  public boolean isConstString(String value, JumboStringMode jumboStringMode) {
    return isConstString(jumboStringMode)
        && ((CfConstString) instruction).getString().toSourceString().equals(value);
  }

  @Override
  public boolean isConstClass() {
    return instruction instanceof CfConstClass;
  }

  @Override
  public boolean isConstClass(String type) {
    return isConstClass() && ((CfConstClass) instruction).getType().toString().equals(type);
  }

  @Override
  public boolean isGoto() {
    return instruction instanceof CfGoto;
  }

  @Override
  public boolean isIfNez() {
    return instruction instanceof CfIf && ((CfIf) instruction).getOpcode() == Opcodes.IFNE;
  }

  @Override
  public boolean isIfEqz() {
    return instruction instanceof CfIf && ((CfIf) instruction).getOpcode() == Opcodes.IFEQ;
  }

  @Override
  public boolean isReturn() {
    return instruction instanceof CfReturn;
  }

  @Override
  public boolean isReturnVoid() {
    return instruction instanceof CfReturnVoid;
  }

  @Override
  public boolean isReturnObject() {
    return instruction instanceof CfReturn
        && ((CfReturn) instruction).getType() == ValueType.OBJECT;
  }

  @Override
  public boolean isThrow() {
    return instruction instanceof CfThrow;
  }

  @Override
  public boolean isNewInstance() {
    return instruction instanceof CfNew;
  }

  @Override
  public boolean isCheckCast() {
    return instruction instanceof CfCheckCast;
  }

  @Override
  public boolean isCheckCast(String type) {
    return isCheckCast() && ((CfCheckCast) instruction).getType().toString().equals(type);
  }

  @Override
  public boolean isInstanceOf() {
    return instruction instanceof CfInstanceOf;
  }

  @Override
  public boolean isIf() {
    return instruction instanceof CfIf || instruction instanceof CfIfCmp;
  }

  @Override
  public boolean isPackedSwitch() {
    return instruction instanceof CfSwitch
        && ((CfSwitch) instruction).getKind() == CfSwitch.Kind.TABLE;
  }

  @Override
  public boolean isSparseSwitch() {
    return instruction instanceof CfSwitch
        && ((CfSwitch) instruction).getKind() == CfSwitch.Kind.LOOKUP;
  }

  public boolean isInvokeSpecial() {
    return instruction instanceof CfInvoke
        && ((CfInvoke) instruction).getOpcode() == Opcodes.INVOKESPECIAL;
  }

  public boolean isInvokeDynamic() {
    return instruction instanceof CfInvokeDynamic;
  }

  public boolean isLabel() {
    return instruction instanceof CfLabel;
  }

  public boolean isPosition() {
    return instruction instanceof CfPosition;
  }

  public boolean isStackInstruction(CfStackInstruction.Opcode opcode) {
    return instruction instanceof CfStackInstruction
        && ((CfStackInstruction) instruction).getOpcode() == opcode;
  }

  public boolean isIfNull() {
    return instruction instanceof CfIf && ((CfIf) instruction).getOpcode() == Opcodes.IFNULL;
  }

  public boolean isLoad() {
    return instruction instanceof CfLoad;
  }

  @Override
  public boolean isMultiplication() {
    if (!(instruction instanceof CfArithmeticBinop)) {
      return false;
    }
    int opcode = ((CfArithmeticBinop) instruction).getAsmOpcode();
    return Opcodes.IMUL <= opcode && opcode <= Opcodes.DMUL;
  }

  @Override
  public boolean isMonitorEnter() {
    if (!(instruction instanceof CfMonitor)) {
      return false;
    }
    CfMonitor monitor = (CfMonitor) instruction;
    return monitor.getType() == Type.ENTER;
  }

  @Override
  public boolean isMonitorExit() {
    if (!(instruction instanceof CfMonitor)) {
      return false;
    }
    CfMonitor monitor = (CfMonitor) instruction;
    return monitor.getType() == Type.EXIT;
  }

  @Override
  public int size() {
    // TODO(b/122302789): CfInstruction#getSize()
    throw new UnsupportedOperationException("CfInstruction doesn't have size yet.");
  }

  @Override
  public InstructionOffsetSubject getOffset(MethodSubject methodSubject) {
    // TODO(b/122302789): CfInstruction#getOffset()
    throw new UnsupportedOperationException("CfInstruction doesn't have offset yet.");
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof CfInstructionSubject
        && instruction.equals(((CfInstructionSubject) other).instruction);
  }

  @Override
  public int hashCode() {
    return instruction.hashCode();
  }

  @Override
  public String toString() {
    return instruction.toString();
  }
}
