// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import com.android.tools.r8.graph.OffsetToObjectMapping;

abstract class DexBaseInstructionFactory {

  static DexInstruction create(
      int high, int opcode, BytecodeStream stream, OffsetToObjectMapping mapping) {
    switch (opcode) {
      case 0x0:
        return DexNop.create(high, stream);
      case DexMove.OPCODE:
        return new DexMove(high, stream);
      case DexMoveFrom16.OPCODE:
        return new DexMoveFrom16(high, stream);
      case DexMove16.OPCODE:
        return new DexMove16(high, stream);
      case DexMoveWide.OPCODE:
        return new DexMoveWide(high, stream);
      case DexMoveWideFrom16.OPCODE:
        return new DexMoveWideFrom16(high, stream);
      case DexMoveWide16.OPCODE:
        return new DexMoveWide16(high, stream);
      case DexMoveObject.OPCODE:
        return new DexMoveObject(high, stream);
      case DexMoveObjectFrom16.OPCODE:
        return new DexMoveObjectFrom16(high, stream);
      case DexMoveObject16.OPCODE:
        return new DexMoveObject16(high, stream);
      case DexMoveResult.OPCODE:
        return new DexMoveResult(high, stream);
      case DexMoveResultWide.OPCODE:
        return new DexMoveResultWide(high, stream);
      case DexMoveResultObject.OPCODE:
        return new DexMoveResultObject(high, stream);
      case DexMoveException.OPCODE:
        return new DexMoveException(high, stream);
      case DexReturnVoid.OPCODE:
        return new DexReturnVoid(high, stream);
      case DexReturn.OPCODE:
        return new DexReturn(high, stream);
      case DexReturnWide.OPCODE:
        return new DexReturnWide(high, stream);
      case DexReturnObject.OPCODE:
        return new DexReturnObject(high, stream);
      case DexConst4.OPCODE:
        return new DexConst4(high, stream);
      case DexConst16.OPCODE:
        return new DexConst16(high, stream);
      case DexConst.OPCODE:
        return new DexConst(high, stream);
      case DexConstHigh16.OPCODE:
        return new DexConstHigh16(high, stream);
      case DexConstWide16.OPCODE:
        return new DexConstWide16(high, stream);
      case DexConstWide32.OPCODE:
        return new DexConstWide32(high, stream);
      case DexConstWide.OPCODE:
        return new DexConstWide(high, stream);
      case DexConstWideHigh16.OPCODE:
        return new DexConstWideHigh16(high, stream);
      case DexConstString.OPCODE:
        return new DexConstString(high, stream, mapping);
      case DexConstStringJumbo.OPCODE:
        return new DexConstStringJumbo(high, stream, mapping);
      case DexConstClass.OPCODE:
        return new DexConstClass(high, stream, mapping);
      case DexMonitorEnter.OPCODE:
        return new DexMonitorEnter(high, stream);
      case DexMonitorExit.OPCODE:
        return new DexMonitorExit(high, stream);
      case DexCheckCast.OPCODE:
        return new DexCheckCast(high, stream, mapping);
      case DexInstanceOf.OPCODE:
        return new DexInstanceOf(high, stream, mapping);
      case DexArrayLength.OPCODE:
        return new DexArrayLength(high, stream);
      case DexNewInstance.OPCODE:
        return new DexNewInstance(high, stream, mapping);
      case DexNewArray.OPCODE:
        return new DexNewArray(high, stream, mapping);
      case DexFilledNewArray.OPCODE:
        return new DexFilledNewArray(high, stream, mapping);
      case DexFilledNewArrayRange.OPCODE:
        return new DexFilledNewArrayRange(high, stream, mapping);
      case DexFillArrayData.OPCODE:
        return new DexFillArrayData(high, stream);
      case DexThrow.OPCODE:
        return new DexThrow(high, stream);
      case DexGoto.OPCODE:
        return new DexGoto(high, stream);
      case DexGoto16.OPCODE:
        return new DexGoto16(high, stream);
      case DexGoto32.OPCODE:
        return new DexGoto32(high, stream);
      case DexPackedSwitch.OPCODE:
        return new DexPackedSwitch(high, stream);
      case DexSparseSwitch.OPCODE:
        return new DexSparseSwitch(high, stream);
      case DexCmplFloat.OPCODE:
        return new DexCmplFloat(high, stream);
      case DexCmpgFloat.OPCODE:
        return new DexCmpgFloat(high, stream);
      case DexCmplDouble.OPCODE:
        return new DexCmplDouble(high, stream);
      case DexCmpgDouble.OPCODE:
        return new DexCmpgDouble(high, stream);
      case DexCmpLong.OPCODE:
        return new DexCmpLong(high, stream);
      case DexIfEq.OPCODE:
        return new DexIfEq(high, stream);
      case DexIfNe.OPCODE:
        return new DexIfNe(high, stream);
      case DexIfLt.OPCODE:
        return new DexIfLt(high, stream);
      case DexIfGe.OPCODE:
        return new DexIfGe(high, stream);
      case DexIfGt.OPCODE:
        return new DexIfGt(high, stream);
      case DexIfLe.OPCODE:
        return new DexIfLe(high, stream);
      case DexIfEqz.OPCODE:
        return new DexIfEqz(high, stream);
      case DexIfNez.OPCODE:
        return new DexIfNez(high, stream);
      case DexIfLtz.OPCODE:
        return new DexIfLtz(high, stream);
      case DexIfGez.OPCODE:
        return new DexIfGez(high, stream);
      case DexIfGtz.OPCODE:
        return new DexIfGtz(high, stream);
      case DexIfLez.OPCODE:
        return new DexIfLez(high, stream);
      case DexAget.OPCODE:
        return new DexAget(high, stream);
      case DexAgetWide.OPCODE:
        return new DexAgetWide(high, stream);
      case DexAgetObject.OPCODE:
        return new DexAgetObject(high, stream);
      case DexAgetBoolean.OPCODE:
        return new DexAgetBoolean(high, stream);
      case DexAgetByte.OPCODE:
        return new DexAgetByte(high, stream);
      case DexAgetChar.OPCODE:
        return new DexAgetChar(high, stream);
      case DexAgetShort.OPCODE:
        return new DexAgetShort(high, stream);
      case DexAput.OPCODE:
        return new DexAput(high, stream);
      case DexAputWide.OPCODE:
        return new DexAputWide(high, stream);
      case DexAputObject.OPCODE:
        return new DexAputObject(high, stream);
      case DexAputBoolean.OPCODE:
        return new DexAputBoolean(high, stream);
      case DexAputByte.OPCODE:
        return new DexAputByte(high, stream);
      case DexAputChar.OPCODE:
        return new DexAputChar(high, stream);
      case DexAputShort.OPCODE:
        return new DexAputShort(high, stream);
      case DexIget.OPCODE:
        return new DexIget(high, stream, mapping);
      case DexIgetWide.OPCODE:
        return new DexIgetWide(high, stream, mapping);
      case DexIgetObject.OPCODE:
        return new DexIgetObject(high, stream, mapping);
      case DexIgetBoolean.OPCODE:
        return new DexIgetBoolean(high, stream, mapping);
      case DexIgetByte.OPCODE:
        return new DexIgetByte(high, stream, mapping);
      case DexIgetChar.OPCODE:
        return new DexIgetChar(high, stream, mapping);
      case DexIgetShort.OPCODE:
        return new DexIgetShort(high, stream, mapping);
      case DexIput.OPCODE:
        return new DexIput(high, stream, mapping);
      case DexIputWide.OPCODE:
        return new DexIputWide(high, stream, mapping);
      case DexIputObject.OPCODE:
        return new DexIputObject(high, stream, mapping);
      case DexIputBoolean.OPCODE:
        return new DexIputBoolean(high, stream, mapping);
      case DexIputByte.OPCODE:
        return new DexIputByte(high, stream, mapping);
      case DexIputChar.OPCODE:
        return new DexIputChar(high, stream, mapping);
      case DexIputShort.OPCODE:
        return new DexIputShort(high, stream, mapping);
      case DexSget.OPCODE:
        return new DexSget(high, stream, mapping);
      case DexSgetWide.OPCODE:
        return new DexSgetWide(high, stream, mapping);
      case DexSgetObject.OPCODE:
        return new DexSgetObject(high, stream, mapping);
      case DexSgetBoolean.OPCODE:
        return new DexSgetBoolean(high, stream, mapping);
      case DexSgetByte.OPCODE:
        return new DexSgetByte(high, stream, mapping);
      case DexSgetChar.OPCODE:
        return new DexSgetChar(high, stream, mapping);
      case DexSgetShort.OPCODE:
        return new DexSgetShort(high, stream, mapping);
      case DexSput.OPCODE:
        return new DexSput(high, stream, mapping);
      case DexSputWide.OPCODE:
        return new DexSputWide(high, stream, mapping);
      case DexSputObject.OPCODE:
        return new DexSputObject(high, stream, mapping);
      case DexSputBoolean.OPCODE:
        return new DexSputBoolean(high, stream, mapping);
      case DexSputByte.OPCODE:
        return new DexSputByte(high, stream, mapping);
      case DexSputChar.OPCODE:
        return new DexSputChar(high, stream, mapping);
      case DexSputShort.OPCODE:
        return new DexSputShort(high, stream, mapping);
      case DexInvokeVirtual.OPCODE:
        return new DexInvokeVirtual(high, stream, mapping);
      case DexInvokeSuper.OPCODE:
        return new DexInvokeSuper(high, stream, mapping);
      case DexInvokeDirect.OPCODE:
        return new DexInvokeDirect(high, stream, mapping);
      case DexInvokeStatic.OPCODE:
        return new DexInvokeStatic(high, stream, mapping);
      case DexInvokeInterface.OPCODE:
        return new DexInvokeInterface(high, stream, mapping);
      case DexInvokeVirtualRange.OPCODE:
        return new DexInvokeVirtualRange(high, stream, mapping);
      case DexInvokeSuperRange.OPCODE:
        return new DexInvokeSuperRange(high, stream, mapping);
      case DexInvokeDirectRange.OPCODE:
        return new DexInvokeDirectRange(high, stream, mapping);
      case DexInvokeStaticRange.OPCODE:
        return new DexInvokeStaticRange(high, stream, mapping);
      case DexInvokeInterfaceRange.OPCODE:
        return new DexInvokeInterfaceRange(high, stream, mapping);
      case DexNegInt.OPCODE:
        return new DexNegInt(high, stream);
      case DexNotInt.OPCODE:
        return new DexNotInt(high, stream);
      case DexNegLong.OPCODE:
        return new DexNegLong(high, stream);
      case DexNotLong.OPCODE:
        return new DexNotLong(high, stream);
      case DexNegFloat.OPCODE:
        return new DexNegFloat(high, stream);
      case DexNegDouble.OPCODE:
        return new DexNegDouble(high, stream);
      case DexIntToLong.OPCODE:
        return new DexIntToLong(high, stream);
      case DexIntToFloat.OPCODE:
        return new DexIntToFloat(high, stream);
      case DexIntToDouble.OPCODE:
        return new DexIntToDouble(high, stream);
      case DexLongToInt.OPCODE:
        return new DexLongToInt(high, stream);
      case DexLongToFloat.OPCODE:
        return new DexLongToFloat(high, stream);
      case DexLongToDouble.OPCODE:
        return new DexLongToDouble(high, stream);
      case DexFloatToInt.OPCODE:
        return new DexFloatToInt(high, stream);
      case DexFloatToLong.OPCODE:
        return new DexFloatToLong(high, stream);
      case DexFloatToDouble.OPCODE:
        return new DexFloatToDouble(high, stream);
      case DexDoubleToInt.OPCODE:
        return new DexDoubleToInt(high, stream);
      case DexDoubleToLong.OPCODE:
        return new DexDoubleToLong(high, stream);
      case DexDoubleToFloat.OPCODE:
        return new DexDoubleToFloat(high, stream);
      case DexIntToByte.OPCODE:
        return new DexIntToByte(high, stream);
      case DexIntToChar.OPCODE:
        return new DexIntToChar(high, stream);
      case DexIntToShort.OPCODE:
        return new DexIntToShort(high, stream);
      case DexAddInt.OPCODE:
        return new DexAddInt(high, stream);
      case DexSubInt.OPCODE:
        return new DexSubInt(high, stream);
      case DexMulInt.OPCODE:
        return new DexMulInt(high, stream);
      case DexDivInt.OPCODE:
        return new DexDivInt(high, stream);
      case DexRemInt.OPCODE:
        return new DexRemInt(high, stream);
      case DexAndInt.OPCODE:
        return new DexAndInt(high, stream);
      case DexOrInt.OPCODE:
        return new DexOrInt(high, stream);
      case DexXorInt.OPCODE:
        return new DexXorInt(high, stream);
      case DexShlInt.OPCODE:
        return new DexShlInt(high, stream);
      case DexShrInt.OPCODE:
        return new DexShrInt(high, stream);
      case DexUshrInt.OPCODE:
        return new DexUshrInt(high, stream);
      case DexAddLong.OPCODE:
        return new DexAddLong(high, stream);
      case DexSubLong.OPCODE:
        return new DexSubLong(high, stream);
      case DexMulLong.OPCODE:
        return new DexMulLong(high, stream);
      case DexDivLong.OPCODE:
        return new DexDivLong(high, stream);
      case DexRemLong.OPCODE:
        return new DexRemLong(high, stream);
      case DexAndLong.OPCODE:
        return new DexAndLong(high, stream);
      case DexOrLong.OPCODE:
        return new DexOrLong(high, stream);
      case DexXorLong.OPCODE:
        return new DexXorLong(high, stream);
      case DexShlLong.OPCODE:
        return new DexShlLong(high, stream);
      case DexShrLong.OPCODE:
        return new DexShrLong(high, stream);
      case DexUshrLong.OPCODE:
        return new DexUshrLong(high, stream);
      case DexAddFloat.OPCODE:
        return new DexAddFloat(high, stream);
      case DexSubFloat.OPCODE:
        return new DexSubFloat(high, stream);
      case DexMulFloat.OPCODE:
        return new DexMulFloat(high, stream);
      case DexDivFloat.OPCODE:
        return new DexDivFloat(high, stream);
      case DexRemFloat.OPCODE:
        return new DexRemFloat(high, stream);
      case DexAddDouble.OPCODE:
        return new DexAddDouble(high, stream);
      case DexSubDouble.OPCODE:
        return new DexSubDouble(high, stream);
      case DexMulDouble.OPCODE:
        return new DexMulDouble(high, stream);
      case DexDivDouble.OPCODE:
        return new DexDivDouble(high, stream);
      case DexRemDouble.OPCODE:
        return new DexRemDouble(high, stream);
      case DexAddInt2Addr.OPCODE:
        return new DexAddInt2Addr(high, stream);
      case DexSubInt2Addr.OPCODE:
        return new DexSubInt2Addr(high, stream);
      case DexMulInt2Addr.OPCODE:
        return new DexMulInt2Addr(high, stream);
      case DexDivInt2Addr.OPCODE:
        return new DexDivInt2Addr(high, stream);
      case DexRemInt2Addr.OPCODE:
        return new DexRemInt2Addr(high, stream);
      case DexAndInt2Addr.OPCODE:
        return new DexAndInt2Addr(high, stream);
      case DexOrInt2Addr.OPCODE:
        return new DexOrInt2Addr(high, stream);
      case DexXorInt2Addr.OPCODE:
        return new DexXorInt2Addr(high, stream);
      case DexShlInt2Addr.OPCODE:
        return new DexShlInt2Addr(high, stream);
      case DexShrInt2Addr.OPCODE:
        return new DexShrInt2Addr(high, stream);
      case DexUshrInt2Addr.OPCODE:
        return new DexUshrInt2Addr(high, stream);
      case DexAddLong2Addr.OPCODE:
        return new DexAddLong2Addr(high, stream);
      case DexSubLong2Addr.OPCODE:
        return new DexSubLong2Addr(high, stream);
      case DexMulLong2Addr.OPCODE:
        return new DexMulLong2Addr(high, stream);
      case DexDivLong2Addr.OPCODE:
        return new DexDivLong2Addr(high, stream);
      case DexRemLong2Addr.OPCODE:
        return new DexRemLong2Addr(high, stream);
      case DexAndLong2Addr.OPCODE:
        return new DexAndLong2Addr(high, stream);
      case DexOrLong2Addr.OPCODE:
        return new DexOrLong2Addr(high, stream);
      case DexXorLong2Addr.OPCODE:
        return new DexXorLong2Addr(high, stream);
      case DexShlLong2Addr.OPCODE:
        return new DexShlLong2Addr(high, stream);
      case DexShrLong2Addr.OPCODE:
        return new DexShrLong2Addr(high, stream);
      case DexUshrLong2Addr.OPCODE:
        return new DexUshrLong2Addr(high, stream);
      case DexAddFloat2Addr.OPCODE:
        return new DexAddFloat2Addr(high, stream);
      case DexSubFloat2Addr.OPCODE:
        return new DexSubFloat2Addr(high, stream);
      case DexMulFloat2Addr.OPCODE:
        return new DexMulFloat2Addr(high, stream);
      case DexDivFloat2Addr.OPCODE:
        return new DexDivFloat2Addr(high, stream);
      case DexRemFloat2Addr.OPCODE:
        return new DexRemFloat2Addr(high, stream);
      case DexAddDouble2Addr.OPCODE:
        return new DexAddDouble2Addr(high, stream);
      case DexSubDouble2Addr.OPCODE:
        return new DexSubDouble2Addr(high, stream);
      case DexMulDouble2Addr.OPCODE:
        return new DexMulDouble2Addr(high, stream);
      case DexDivDouble2Addr.OPCODE:
        return new DexDivDouble2Addr(high, stream);
      case DexRemDouble2Addr.OPCODE:
        return new DexRemDouble2Addr(high, stream);
      case DexAddIntLit16.OPCODE:
        return new DexAddIntLit16(high, stream);
      case DexRsubInt.OPCODE:
        return new DexRsubInt(high, stream);
      case DexMulIntLit16.OPCODE:
        return new DexMulIntLit16(high, stream);
      case DexDivIntLit16.OPCODE:
        return new DexDivIntLit16(high, stream);
      case DexRemIntLit16.OPCODE:
        return new DexRemIntLit16(high, stream);
      case DexAndIntLit16.OPCODE:
        return new DexAndIntLit16(high, stream);
      case DexOrIntLit16.OPCODE:
        return new DexOrIntLit16(high, stream);
      case DexXorIntLit16.OPCODE:
        return new DexXorIntLit16(high, stream);
      case DexAddIntLit8.OPCODE:
        return new DexAddIntLit8(high, stream);
      case DexRsubIntLit8.OPCODE:
        return new DexRsubIntLit8(high, stream);
      case DexMulIntLit8.OPCODE:
        return new DexMulIntLit8(high, stream);
      case DexDivIntLit8.OPCODE:
        return new DexDivIntLit8(high, stream);
      case DexRemIntLit8.OPCODE:
        return new DexRemIntLit8(high, stream);
      case DexAndIntLit8.OPCODE:
        return new DexAndIntLit8(high, stream);
      case DexOrIntLit8.OPCODE:
        return new DexOrIntLit8(high, stream);
      case DexXorIntLit8.OPCODE:
        return new DexXorIntLit8(high, stream);
      case DexShlIntLit8.OPCODE:
        return new DexShlIntLit8(high, stream);
      case DexShrIntLit8.OPCODE:
        return new DexShrIntLit8(high, stream);
      case DexUshrIntLit8.OPCODE:
        return new DexUshrIntLit8(high, stream);
      case DexInvokePolymorphic.OPCODE:
        return new DexInvokePolymorphic(high, stream, mapping);
      case DexInvokePolymorphicRange.OPCODE:
        return new DexInvokePolymorphicRange(high, stream, mapping);
      case DexInvokeCustom.OPCODE:
        return new DexInvokeCustom(high, stream, mapping);
      case DexInvokeCustomRange.OPCODE:
        return new DexInvokeCustomRange(high, stream, mapping);
      case DexConstMethodHandle.OPCODE:
        return new DexConstMethodHandle(high, stream, mapping);
      case DexConstMethodType.OPCODE:
        return new DexConstMethodType(high, stream, mapping);
      default:
        throw new IllegalArgumentException("Illegal Opcode: 0x" + Integer.toString(opcode, 16));
    }
  }
}
