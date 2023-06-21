// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.dex.code.DexAddInt;
import com.android.tools.r8.dex.code.DexAddInt2Addr;
import com.android.tools.r8.dex.code.DexAddIntLit16;
import com.android.tools.r8.dex.code.DexAddIntLit8;
import com.android.tools.r8.dex.code.DexAddLong;
import com.android.tools.r8.dex.code.DexAddLong2Addr;
import com.android.tools.r8.dex.code.DexAget;
import com.android.tools.r8.dex.code.DexAgetBoolean;
import com.android.tools.r8.dex.code.DexAgetByte;
import com.android.tools.r8.dex.code.DexAgetChar;
import com.android.tools.r8.dex.code.DexAgetObject;
import com.android.tools.r8.dex.code.DexAgetShort;
import com.android.tools.r8.dex.code.DexAgetWide;
import com.android.tools.r8.dex.code.DexAndInt;
import com.android.tools.r8.dex.code.DexAndInt2Addr;
import com.android.tools.r8.dex.code.DexAndIntLit16;
import com.android.tools.r8.dex.code.DexAndIntLit8;
import com.android.tools.r8.dex.code.DexAndLong;
import com.android.tools.r8.dex.code.DexAndLong2Addr;
import com.android.tools.r8.dex.code.DexAput;
import com.android.tools.r8.dex.code.DexAputBoolean;
import com.android.tools.r8.dex.code.DexAputByte;
import com.android.tools.r8.dex.code.DexAputChar;
import com.android.tools.r8.dex.code.DexAputObject;
import com.android.tools.r8.dex.code.DexAputShort;
import com.android.tools.r8.dex.code.DexAputWide;
import com.android.tools.r8.dex.code.DexArrayLength;
import com.android.tools.r8.dex.code.DexCheckCast;
import com.android.tools.r8.dex.code.DexConst;
import com.android.tools.r8.dex.code.DexConst16;
import com.android.tools.r8.dex.code.DexConst4;
import com.android.tools.r8.dex.code.DexConstClass;
import com.android.tools.r8.dex.code.DexConstHigh16;
import com.android.tools.r8.dex.code.DexConstString;
import com.android.tools.r8.dex.code.DexConstStringJumbo;
import com.android.tools.r8.dex.code.DexConstWide;
import com.android.tools.r8.dex.code.DexConstWide16;
import com.android.tools.r8.dex.code.DexConstWide32;
import com.android.tools.r8.dex.code.DexConstWideHigh16;
import com.android.tools.r8.dex.code.DexDivInt;
import com.android.tools.r8.dex.code.DexDivInt2Addr;
import com.android.tools.r8.dex.code.DexDivIntLit16;
import com.android.tools.r8.dex.code.DexDivIntLit8;
import com.android.tools.r8.dex.code.DexDivLong;
import com.android.tools.r8.dex.code.DexDivLong2Addr;
import com.android.tools.r8.dex.code.DexFilledNewArray;
import com.android.tools.r8.dex.code.DexGoto;
import com.android.tools.r8.dex.code.DexIfEq;
import com.android.tools.r8.dex.code.DexIfEqz;
import com.android.tools.r8.dex.code.DexIfGe;
import com.android.tools.r8.dex.code.DexIfGez;
import com.android.tools.r8.dex.code.DexIfGt;
import com.android.tools.r8.dex.code.DexIfGtz;
import com.android.tools.r8.dex.code.DexIfLe;
import com.android.tools.r8.dex.code.DexIfLez;
import com.android.tools.r8.dex.code.DexIfLt;
import com.android.tools.r8.dex.code.DexIfLtz;
import com.android.tools.r8.dex.code.DexIfNe;
import com.android.tools.r8.dex.code.DexIfNez;
import com.android.tools.r8.dex.code.DexIget;
import com.android.tools.r8.dex.code.DexIgetBoolean;
import com.android.tools.r8.dex.code.DexIgetByte;
import com.android.tools.r8.dex.code.DexIgetChar;
import com.android.tools.r8.dex.code.DexIgetObject;
import com.android.tools.r8.dex.code.DexIgetShort;
import com.android.tools.r8.dex.code.DexIgetWide;
import com.android.tools.r8.dex.code.DexInstanceOf;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.dex.code.DexInvokeCustom;
import com.android.tools.r8.dex.code.DexInvokeCustomRange;
import com.android.tools.r8.dex.code.DexInvokeDirect;
import com.android.tools.r8.dex.code.DexInvokeDirectRange;
import com.android.tools.r8.dex.code.DexInvokeInterface;
import com.android.tools.r8.dex.code.DexInvokeInterfaceRange;
import com.android.tools.r8.dex.code.DexInvokeStatic;
import com.android.tools.r8.dex.code.DexInvokeStaticRange;
import com.android.tools.r8.dex.code.DexInvokeSuper;
import com.android.tools.r8.dex.code.DexInvokeSuperRange;
import com.android.tools.r8.dex.code.DexInvokeVirtual;
import com.android.tools.r8.dex.code.DexInvokeVirtualRange;
import com.android.tools.r8.dex.code.DexIput;
import com.android.tools.r8.dex.code.DexIputBoolean;
import com.android.tools.r8.dex.code.DexIputByte;
import com.android.tools.r8.dex.code.DexIputChar;
import com.android.tools.r8.dex.code.DexIputObject;
import com.android.tools.r8.dex.code.DexIputShort;
import com.android.tools.r8.dex.code.DexIputWide;
import com.android.tools.r8.dex.code.DexMonitorEnter;
import com.android.tools.r8.dex.code.DexMonitorExit;
import com.android.tools.r8.dex.code.DexMulDouble;
import com.android.tools.r8.dex.code.DexMulDouble2Addr;
import com.android.tools.r8.dex.code.DexMulFloat;
import com.android.tools.r8.dex.code.DexMulFloat2Addr;
import com.android.tools.r8.dex.code.DexMulInt;
import com.android.tools.r8.dex.code.DexMulInt2Addr;
import com.android.tools.r8.dex.code.DexMulIntLit16;
import com.android.tools.r8.dex.code.DexMulIntLit8;
import com.android.tools.r8.dex.code.DexMulLong;
import com.android.tools.r8.dex.code.DexMulLong2Addr;
import com.android.tools.r8.dex.code.DexNewArray;
import com.android.tools.r8.dex.code.DexNewInstance;
import com.android.tools.r8.dex.code.DexNop;
import com.android.tools.r8.dex.code.DexOrInt;
import com.android.tools.r8.dex.code.DexOrInt2Addr;
import com.android.tools.r8.dex.code.DexOrIntLit16;
import com.android.tools.r8.dex.code.DexOrIntLit8;
import com.android.tools.r8.dex.code.DexOrLong;
import com.android.tools.r8.dex.code.DexOrLong2Addr;
import com.android.tools.r8.dex.code.DexPackedSwitch;
import com.android.tools.r8.dex.code.DexRemInt;
import com.android.tools.r8.dex.code.DexRemInt2Addr;
import com.android.tools.r8.dex.code.DexRemIntLit16;
import com.android.tools.r8.dex.code.DexRemIntLit8;
import com.android.tools.r8.dex.code.DexRemLong;
import com.android.tools.r8.dex.code.DexRemLong2Addr;
import com.android.tools.r8.dex.code.DexReturn;
import com.android.tools.r8.dex.code.DexReturnObject;
import com.android.tools.r8.dex.code.DexReturnVoid;
import com.android.tools.r8.dex.code.DexRsubInt;
import com.android.tools.r8.dex.code.DexRsubIntLit8;
import com.android.tools.r8.dex.code.DexSget;
import com.android.tools.r8.dex.code.DexSgetBoolean;
import com.android.tools.r8.dex.code.DexSgetByte;
import com.android.tools.r8.dex.code.DexSgetChar;
import com.android.tools.r8.dex.code.DexSgetObject;
import com.android.tools.r8.dex.code.DexSgetShort;
import com.android.tools.r8.dex.code.DexSgetWide;
import com.android.tools.r8.dex.code.DexShlInt;
import com.android.tools.r8.dex.code.DexShlInt2Addr;
import com.android.tools.r8.dex.code.DexShlIntLit8;
import com.android.tools.r8.dex.code.DexShlLong;
import com.android.tools.r8.dex.code.DexShlLong2Addr;
import com.android.tools.r8.dex.code.DexShrInt;
import com.android.tools.r8.dex.code.DexShrInt2Addr;
import com.android.tools.r8.dex.code.DexShrIntLit8;
import com.android.tools.r8.dex.code.DexShrLong;
import com.android.tools.r8.dex.code.DexShrLong2Addr;
import com.android.tools.r8.dex.code.DexSparseSwitch;
import com.android.tools.r8.dex.code.DexSput;
import com.android.tools.r8.dex.code.DexSputBoolean;
import com.android.tools.r8.dex.code.DexSputByte;
import com.android.tools.r8.dex.code.DexSputChar;
import com.android.tools.r8.dex.code.DexSputObject;
import com.android.tools.r8.dex.code.DexSputShort;
import com.android.tools.r8.dex.code.DexSputWide;
import com.android.tools.r8.dex.code.DexSubInt;
import com.android.tools.r8.dex.code.DexSubInt2Addr;
import com.android.tools.r8.dex.code.DexSubLong;
import com.android.tools.r8.dex.code.DexSubLong2Addr;
import com.android.tools.r8.dex.code.DexThrow;
import com.android.tools.r8.dex.code.DexUshrInt;
import com.android.tools.r8.dex.code.DexUshrInt2Addr;
import com.android.tools.r8.dex.code.DexUshrIntLit8;
import com.android.tools.r8.dex.code.DexUshrLong;
import com.android.tools.r8.dex.code.DexUshrLong2Addr;
import com.android.tools.r8.dex.code.DexXorInt;
import com.android.tools.r8.dex.code.DexXorInt2Addr;
import com.android.tools.r8.dex.code.DexXorIntLit16;
import com.android.tools.r8.dex.code.DexXorIntLit8;
import com.android.tools.r8.dex.code.DexXorLong;
import com.android.tools.r8.dex.code.DexXorLong2Addr;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.code.SingleConstant;
import com.android.tools.r8.ir.code.WideConstant;

public class DexInstructionSubject implements InstructionSubject {

  protected final DexInstruction instruction;
  protected final MethodSubject method;

  public DexInstructionSubject(DexInstruction instruction, MethodSubject method) {
    this.instruction = instruction;
    this.method = method;
  }

  @Override
  public boolean isDexInstruction() {
    return true;
  }

  @Override
  public DexInstructionSubject asDexInstruction() {
    return this;
  }

  @Override
  public boolean isCfInstruction() {
    return false;
  }

  @Override
  public CfInstructionSubject asCfInstruction() {
    return null;
  }

  @Override
  public boolean isFieldAccess() {
    return isInstanceGet() || isInstancePut() || isStaticGet() || isStaticPut();
  }

  @Override
  public boolean isInstanceGet() {
    return instruction instanceof DexIget
        || instruction instanceof DexIgetBoolean
        || instruction instanceof DexIgetByte
        || instruction instanceof DexIgetShort
        || instruction instanceof DexIgetChar
        || instruction instanceof DexIgetWide
        || instruction instanceof DexIgetObject;
  }

  @Override
  public boolean isInstancePut() {
    return instruction instanceof DexIput
        || instruction instanceof DexIputBoolean
        || instruction instanceof DexIputByte
        || instruction instanceof DexIputShort
        || instruction instanceof DexIputChar
        || instruction instanceof DexIputWide
        || instruction instanceof DexIputObject;
  }

  @Override
  public boolean isStaticGet() {
    return instruction instanceof DexSget
        || instruction instanceof DexSgetBoolean
        || instruction instanceof DexSgetByte
        || instruction instanceof DexSgetShort
        || instruction instanceof DexSgetChar
        || instruction instanceof DexSgetWide
        || instruction instanceof DexSgetObject;
  }

  @Override
  public boolean isStaticPut() {
    return instruction instanceof DexSput
        || instruction instanceof DexSputBoolean
        || instruction instanceof DexSputByte
        || instruction instanceof DexSputShort
        || instruction instanceof DexSputChar
        || instruction instanceof DexSputWide
        || instruction instanceof DexSputObject;
  }

  @Override
  public DexField getField() {
    assert isFieldAccess();
    return instruction.getField();
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
  public boolean isInvokeMethod() {
    return isInvoke();
  }

  @Override
  public boolean isInvokeVirtual() {
    return instruction instanceof DexInvokeVirtual || instruction instanceof DexInvokeVirtualRange;
  }

  @Override
  public boolean isInvokeInterface() {
    return instruction instanceof DexInvokeInterface
        || instruction instanceof DexInvokeInterfaceRange;
  }

  @Override
  public boolean isInvokeStatic() {
    return instruction instanceof DexInvokeStatic || instruction instanceof DexInvokeStaticRange;
  }

  @Override
  public boolean isInvokeDynamic() {
    return isInvokeCustom();
  }

  public boolean isInvokeCustom() {
    return instruction instanceof DexInvokeCustom || instruction instanceof DexInvokeCustomRange;
  }

  public boolean isInvokeSuper() {
    return instruction instanceof DexInvokeSuper || instruction instanceof DexInvokeSuperRange;
  }

  public boolean isInvokeDirect() {
    return instruction instanceof DexInvokeDirect || instruction instanceof DexInvokeDirectRange;
  }

  @Override
  public DexMethod getMethod() {
    assert isInvoke();
    return instruction.getMethod();
  }

  @Override
  public boolean isNop() {
    return instruction instanceof DexNop;
  }

  @Override
  public boolean isConstNumber() {
    return instruction instanceof DexConst
        || instruction instanceof DexConst4
        || instruction instanceof DexConst16
        || instruction instanceof DexConstHigh16
        || instruction instanceof DexConstWide
        || instruction instanceof DexConstWide16
        || instruction instanceof DexConstWide32
        || instruction instanceof DexConstWideHigh16;
  }

  @Override
  public boolean isConstNumber(long value) {
    return isConstNumber() && getConstNumber() == value;
  }

  @Override
  public boolean isConstNull() {
    return isConst4() && isConstNumber(0);
  }

  @Override
  public boolean isConstString(JumboStringMode jumboStringMode) {
    return instruction instanceof DexConstString
        || (jumboStringMode == JumboStringMode.ALLOW && instruction instanceof DexConstStringJumbo);
  }

  @Override
  public boolean isConstString(String value, JumboStringMode jumboStringMode) {
    return (instruction instanceof DexConstString
            && ((DexConstString) instruction).BBBB.toSourceString().equals(value))
        || (jumboStringMode == JumboStringMode.ALLOW
            && instruction instanceof DexConstStringJumbo
            && ((DexConstStringJumbo) instruction).BBBBBBBB.toSourceString().equals(value));
  }

  @Override
  public boolean isJumboString() {
    return instruction instanceof DexConstStringJumbo;
  }

  @Override public long getConstNumber() {
    assert isConstNumber();
    if (instruction instanceof SingleConstant) {
      return ((SingleConstant) instruction).decodedValue();
    }
    assert instruction instanceof WideConstant;
    return ((WideConstant) instruction).decodedValue();
  }

  @Override
  public String getConstString() {
    if (instruction instanceof DexConstString) {
      return ((DexConstString) instruction).BBBB.toSourceString();
    }
    if (instruction instanceof DexConstStringJumbo) {
      return ((DexConstStringJumbo) instruction).BBBBBBBB.toSourceString();
    }
    return null;
  }

  @Override
  public boolean isConstClass() {
    return instruction instanceof DexConstClass;
  }

  @Override
  public boolean isConstClass(String type) {
    return isConstClass() && ((DexConstClass) instruction).getType().toString().equals(type);
  }

  @Override
  public boolean isGoto() {

    return instruction instanceof DexGoto;
  }

  @Override
  public boolean isIfNez() {
    return instruction instanceof DexIfNez;
  }

  @Override
  public boolean isIfEq() {
    return instruction instanceof DexIfEq;
  }

  @Override
  public boolean isIfEqz() {
    return instruction instanceof DexIfEqz;
  }

  @Override
  public boolean isIfNull() {
    // Not in DEX.
    return false;
  }

  @Override
  public boolean isIfNonNull() {
    // Not in DEX.
    return false;
  }

  @Override
  public boolean isReturn() {
    return instruction instanceof DexReturn;
  }

  @Override
  public boolean isReturnVoid() {
    return instruction instanceof DexReturnVoid;
  }

  @Override
  public boolean isReturnObject() {
    return instruction instanceof DexReturnObject;
  }

  @Override
  public boolean isThrow() {
    return instruction instanceof DexThrow;
  }

  @Override
  public boolean isNewInstance() {
    return instruction instanceof DexNewInstance;
  }

  @Override
  public boolean isNewInstance(String type) {
    return isNewInstance() && ((DexNewInstance) instruction).getType().toString().equals(type);
  }

  @Override
  public boolean isCheckCast() {
    return instruction instanceof DexCheckCast;
  }

  @Override
  public boolean isCheckCast(String type) {
    return isCheckCast() && ((DexCheckCast) instruction).getType().toString().equals(type);
  }

  @Override
  public boolean isInstanceOf() {
    return instruction instanceof DexInstanceOf;
  }

  @Override
  public boolean isInstanceOf(String type) {
    return isInstanceOf() && ((DexInstanceOf) instruction).getType().toString().equals(type);
  }

  public boolean isConst4() {
    return instruction instanceof DexConst4;
  }

  @Override
  public boolean isIf() {
    return instruction instanceof DexIfEq
        || instruction instanceof DexIfEqz
        || instruction instanceof DexIfGe
        || instruction instanceof DexIfGez
        || instruction instanceof DexIfGt
        || instruction instanceof DexIfGtz
        || instruction instanceof DexIfLe
        || instruction instanceof DexIfLez
        || instruction instanceof DexIfLt
        || instruction instanceof DexIfLtz
        || instruction instanceof DexIfNe
        || instruction instanceof DexIfNez;
  }

  @Override
  public boolean isSwitch() {
    return isPackedSwitch() || isSparseSwitch();
  }

  @Override
  public boolean isPackedSwitch() {
    return instruction instanceof DexPackedSwitch;
  }

  @Override
  public boolean isSparseSwitch() {
    return instruction instanceof DexSparseSwitch;
  }

  public boolean isIntArithmeticBinop() {
    return instruction instanceof DexMulInt
        || instruction instanceof DexMulIntLit8
        || instruction instanceof DexMulIntLit16
        || instruction instanceof DexMulInt2Addr
        || instruction instanceof DexAddInt
        || instruction instanceof DexAddIntLit8
        || instruction instanceof DexAddIntLit16
        || instruction instanceof DexAddInt2Addr
        || instruction instanceof DexRsubInt
        || instruction instanceof DexRsubIntLit8
        || instruction instanceof DexSubInt
        || instruction instanceof DexSubInt2Addr
        || instruction instanceof DexDivInt
        || instruction instanceof DexDivIntLit8
        || instruction instanceof DexDivIntLit16
        || instruction instanceof DexDivInt2Addr
        || instruction instanceof DexRemInt
        || instruction instanceof DexRemIntLit8
        || instruction instanceof DexRemIntLit16
        || instruction instanceof DexRemInt2Addr;
  }

  public boolean isLongArithmeticBinop() {
    return instruction instanceof DexMulLong
        || instruction instanceof DexMulLong2Addr
        || instruction instanceof DexAddLong
        || instruction instanceof DexAddLong2Addr
        || instruction instanceof DexSubLong
        || instruction instanceof DexSubLong2Addr
        || instruction instanceof DexDivLong
        || instruction instanceof DexDivLong2Addr
        || instruction instanceof DexRemLong
        || instruction instanceof DexRemLong2Addr;
  }

  public boolean isIntLogicalBinop() {
    return instruction instanceof DexAndInt
        || instruction instanceof DexAndIntLit8
        || instruction instanceof DexAndIntLit16
        || instruction instanceof DexAndInt2Addr
        || instruction instanceof DexOrInt
        || instruction instanceof DexOrIntLit8
        || instruction instanceof DexOrIntLit16
        || instruction instanceof DexOrInt2Addr
        || instruction instanceof DexXorInt
        || instruction instanceof DexXorIntLit8
        || instruction instanceof DexXorIntLit16
        || instruction instanceof DexXorInt2Addr
        || instruction instanceof DexShrInt
        || instruction instanceof DexShrIntLit8
        || instruction instanceof DexShrInt2Addr
        || instruction instanceof DexShlInt
        || instruction instanceof DexShlIntLit8
        || instruction instanceof DexShlInt2Addr
        || instruction instanceof DexUshrInt
        || instruction instanceof DexUshrIntLit8
        || instruction instanceof DexUshrInt2Addr;
  }

  public boolean isLongLogicalBinop() {
    return instruction instanceof DexAndLong
        || instruction instanceof DexAndLong2Addr
        || instruction instanceof DexOrLong
        || instruction instanceof DexOrLong2Addr
        || instruction instanceof DexXorLong
        || instruction instanceof DexXorLong2Addr
        || instruction instanceof DexShrLong
        || instruction instanceof DexShrLong2Addr
        || instruction instanceof DexShlLong
        || instruction instanceof DexShlLong2Addr
        || instruction instanceof DexUshrLong
        || instruction instanceof DexUshrLong2Addr;
  }

  @Override
  public boolean isMultiplication() {
    return instruction instanceof DexMulInt
        || instruction instanceof DexMulIntLit8
        || instruction instanceof DexMulIntLit16
        || instruction instanceof DexMulInt2Addr
        || instruction instanceof DexMulFloat
        || instruction instanceof DexMulFloat2Addr
        || instruction instanceof DexMulLong
        || instruction instanceof DexMulLong2Addr
        || instruction instanceof DexMulDouble
        || instruction instanceof DexMulDouble2Addr;
  }

  @Override
  public boolean isNewArray() {
    return instruction instanceof DexNewArray;
  }

  @Override
  public boolean isArrayLength() {
    return instruction instanceof DexArrayLength;
  }

  @Override
  public boolean isArrayGet() {
    return instruction instanceof DexAget
        || instruction instanceof DexAgetBoolean
        || instruction instanceof DexAgetByte
        || instruction instanceof DexAgetChar
        || instruction instanceof DexAgetObject
        || instruction instanceof DexAgetShort
        || instruction instanceof DexAgetWide;
  }

  @Override
  public boolean isArrayPut() {
    return instruction instanceof DexAput
        || instruction instanceof DexAputBoolean
        || instruction instanceof DexAputByte
        || instruction instanceof DexAputChar
        || instruction instanceof DexAputObject
        || instruction instanceof DexAputShort
        || instruction instanceof DexAputWide;
  }

  @Override
  public boolean isMonitorEnter() {
    return instruction instanceof DexMonitorEnter;
  }

  @Override
  public boolean isMonitorExit() {
    return instruction instanceof DexMonitorExit;
  }

  @Override
  public boolean isFilledNewArray() {
    return instruction instanceof DexFilledNewArray;
  }

  @Override
  public int size() {
    return instruction.getSize();
  }

  @Override
  public InstructionOffsetSubject getOffset(MethodSubject methodSubject) {
    return new InstructionOffsetSubject(instruction.getOffset());
  }

  @Override
  public MethodSubject getMethodSubject() {
    return method;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof DexInstructionSubject
        && instruction.equals(((DexInstructionSubject) other).instruction);
  }

  @Override
  public int hashCode() {
    return instruction.hashCode();
  }

  @Override
  public String toString() {
    return instruction.toString();
  }

  public DexInstruction getInstruction() {
    return instruction;
  }
}
