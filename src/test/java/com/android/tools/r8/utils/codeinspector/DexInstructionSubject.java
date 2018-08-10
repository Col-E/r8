// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.code.Const4;
import com.android.tools.r8.code.ConstString;
import com.android.tools.r8.code.ConstStringJumbo;
import com.android.tools.r8.code.Goto;
import com.android.tools.r8.code.IfEqz;
import com.android.tools.r8.code.IfNez;
import com.android.tools.r8.code.Iget;
import com.android.tools.r8.code.IgetBoolean;
import com.android.tools.r8.code.IgetByte;
import com.android.tools.r8.code.IgetChar;
import com.android.tools.r8.code.IgetObject;
import com.android.tools.r8.code.IgetShort;
import com.android.tools.r8.code.IgetWide;
import com.android.tools.r8.code.Instruction;
import com.android.tools.r8.code.InvokeDirect;
import com.android.tools.r8.code.InvokeDirectRange;
import com.android.tools.r8.code.InvokeInterface;
import com.android.tools.r8.code.InvokeInterfaceRange;
import com.android.tools.r8.code.InvokeStatic;
import com.android.tools.r8.code.InvokeStaticRange;
import com.android.tools.r8.code.InvokeSuper;
import com.android.tools.r8.code.InvokeSuperRange;
import com.android.tools.r8.code.InvokeVirtual;
import com.android.tools.r8.code.InvokeVirtualRange;
import com.android.tools.r8.code.Iput;
import com.android.tools.r8.code.IputBoolean;
import com.android.tools.r8.code.IputByte;
import com.android.tools.r8.code.IputChar;
import com.android.tools.r8.code.IputObject;
import com.android.tools.r8.code.IputShort;
import com.android.tools.r8.code.IputWide;
import com.android.tools.r8.code.NewInstance;
import com.android.tools.r8.code.Nop;
import com.android.tools.r8.code.ReturnVoid;
import com.android.tools.r8.code.Sget;
import com.android.tools.r8.code.SgetBoolean;
import com.android.tools.r8.code.SgetByte;
import com.android.tools.r8.code.SgetChar;
import com.android.tools.r8.code.SgetObject;
import com.android.tools.r8.code.SgetShort;
import com.android.tools.r8.code.SgetWide;
import com.android.tools.r8.code.Sput;
import com.android.tools.r8.code.SputBoolean;
import com.android.tools.r8.code.SputByte;
import com.android.tools.r8.code.SputChar;
import com.android.tools.r8.code.SputObject;
import com.android.tools.r8.code.SputShort;
import com.android.tools.r8.code.SputWide;
import com.android.tools.r8.code.Throw;

public class DexInstructionSubject implements InstructionSubject {
  protected final Instruction instruction;

  public DexInstructionSubject(Instruction instruction) {
    this.instruction = instruction;
  }

  @Override
  public boolean isFieldAccess() {
    return isInstanceGet() || isInstancePut() || isStaticGet() || isStaticPut();
  }

  @Override
  public boolean isInvokeVirtual() {
    return instruction instanceof InvokeVirtual || instruction instanceof InvokeVirtualRange;
  }

  @Override
  public boolean isInvokeInterface() {
    return instruction instanceof InvokeInterface || instruction instanceof InvokeInterfaceRange;
  }

  @Override
  public boolean isInvokeStatic() {
    return instruction instanceof InvokeStatic || instruction instanceof InvokeStaticRange;
  }

  @Override
  public boolean isNop() {
    return instruction instanceof Nop;
  }

  @Override
  public boolean isConstString(JumboStringMode jumboStringMode) {
    return instruction instanceof ConstString
        || (jumboStringMode == JumboStringMode.ALLOW && instruction instanceof ConstStringJumbo);
  }

  @Override
  public boolean isConstString(String value, JumboStringMode jumboStringMode) {
    return (instruction instanceof ConstString
            && ((ConstString) instruction).BBBB.toSourceString().equals(value))
        || (jumboStringMode == JumboStringMode.ALLOW
            && instruction instanceof ConstStringJumbo
            && ((ConstStringJumbo) instruction).BBBBBBBB.toSourceString().equals(value));
  }

  @Override
  public boolean isGoto() {

    return instruction instanceof Goto;
  }

  @Override
  public boolean isIfNez() {
    return instruction instanceof IfNez;
  }

  @Override
  public boolean isIfEqz() {
    return instruction instanceof IfEqz;
  }

  @Override
  public boolean isReturnVoid() {
    return instruction instanceof ReturnVoid;
  }

  @Override
  public boolean isThrow() {
    return instruction instanceof Throw;
  }

  @Override
  public boolean isInvoke() {
    return isInvokeVirtual()
        || isInvokeInterface()
        || isInvokeDirect()
        || isInvokeSuper()
        || isInvokeStatic();
  }

  @Override
  public boolean isNewInstance() {
    return instruction instanceof NewInstance;
  }

  public boolean isInvokeSuper() {
    return instruction instanceof InvokeSuper || instruction instanceof InvokeSuperRange;
  }

  public boolean isInvokeDirect() {
    return instruction instanceof InvokeDirect || instruction instanceof InvokeDirectRange;
  }

  public boolean isConst4() {
    return instruction instanceof Const4;
  }

  @Override
  public boolean isInstanceGet() {
    return instruction instanceof Iget
        || instruction instanceof IgetBoolean
        || instruction instanceof IgetByte
        || instruction instanceof IgetShort
        || instruction instanceof IgetChar
        || instruction instanceof IgetWide
        || instruction instanceof IgetObject;
  }

  @Override
  public boolean isInstancePut() {
    return instruction instanceof Iput
        || instruction instanceof IputBoolean
        || instruction instanceof IputByte
        || instruction instanceof IputShort
        || instruction instanceof IputChar
        || instruction instanceof IputWide
        || instruction instanceof IputObject;
  }

  @Override
  public boolean isStaticGet() {
    return instruction instanceof Sget
        || instruction instanceof SgetBoolean
        || instruction instanceof SgetByte
        || instruction instanceof SgetShort
        || instruction instanceof SgetChar
        || instruction instanceof SgetWide
        || instruction instanceof SgetObject;
  }

  @Override
  public boolean isStaticPut() {
    return instruction instanceof Sput
        || instruction instanceof SputBoolean
        || instruction instanceof SputByte
        || instruction instanceof SputShort
        || instruction instanceof SputChar
        || instruction instanceof SputWide
        || instruction instanceof SputObject;
  }
}
