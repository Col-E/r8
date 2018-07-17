// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.dexinspector;

import com.android.tools.r8.cf.code.CfConstString;
import com.android.tools.r8.cf.code.CfFieldInstruction;
import com.android.tools.r8.cf.code.CfGoto;
import com.android.tools.r8.cf.code.CfIf;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfInvokeDynamic;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfNew;
import com.android.tools.r8.cf.code.CfNop;
import com.android.tools.r8.cf.code.CfPosition;
import com.android.tools.r8.cf.code.CfReturnVoid;
import com.android.tools.r8.cf.code.CfThrow;
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
  public boolean isNop() {
    return instruction instanceof CfNop;
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
  public boolean isReturnVoid() {
    return instruction instanceof CfReturnVoid;
  }

  @Override
  public boolean isThrow() {
    return instruction instanceof CfThrow;
  }

  @Override
  public boolean isInvoke() {
    return instruction instanceof CfInvoke || instruction instanceof CfInvokeDynamic;
  }

  @Override
  public boolean isNewInstance() {
    return instruction instanceof CfNew;
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
}
