// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.retrace.RetraceFrameResult;
import com.android.tools.r8.retrace.RetraceMethodResult;
import com.android.tools.r8.retrace.RetraceStackTraceContext;
import com.android.tools.r8.retrace.Retracer;
import java.util.OptionalInt;

public interface InstructionSubject {

  enum JumboStringMode {
    ALLOW,
    DISALLOW
  };

  boolean isDexInstruction();

  DexInstructionSubject asDexInstruction();

  boolean isCfInstruction();

  CfInstructionSubject asCfInstruction();

  boolean isFieldAccess();

  boolean isInstancePut();

  boolean isStaticPut();

  boolean isInstanceGet();

  boolean isStaticGet();

  DexField getField();

  boolean isInvoke();

  boolean isInvokeMethod();

  boolean isInvokeVirtual();

  boolean isInvokeInterface();

  boolean isInvokeStatic();

  boolean isInvokeDynamic();

  default boolean isInvokeSpecialOrDirect() {
    if (isCfInstruction()) {
      return asCfInstruction().isInvokeSpecial();
    } else {
      assert isDexInstruction();
      return asDexInstruction().isInvokeDirect();
    }
  }

  DexMethod getMethod();

  boolean isNop();

  boolean isConstNumber();

  boolean isConstNumber(long value);

  boolean isConstNull();

  default boolean isConstString() {
    return isConstString(JumboStringMode.ALLOW);
  }

  boolean isConstString(JumboStringMode jumboStringMode);

  boolean isConstString(String value, JumboStringMode jumboStringMode);

  default boolean isConstString(String value) {
    return isConstString(value, JumboStringMode.ALLOW);
  }

  boolean isJumboString();

  long getConstNumber();

  String getConstString();

  boolean isConstClass();

  boolean isConstClass(String type);

  boolean isGoto();

  boolean isPop();

  boolean isIfNez();

  boolean isIfEq();

  boolean isIfEqz();

  boolean isIfNull();

  boolean isIfNonNull();

  boolean isReturn();

  boolean isReturnVoid();

  boolean isReturnObject();

  boolean isThrow();

  boolean isNewInstance();

  boolean isNewInstance(String type);

  boolean isCheckCast();

  boolean isCheckCast(String type);

  default CheckCastInstructionSubject asCheckCast() {
    return null;
  }

  boolean isInstanceOf();

  boolean isInstanceOf(String type);

  boolean isIf(); // Also include CF/if_cmp* instructions.

  boolean isSwitch();

  default SwitchInstructionSubject asSwitch() {
    return null;
  }

  boolean isPackedSwitch();

  boolean isSparseSwitch();

  boolean isIntArithmeticBinop();

  boolean isIntLogicalBinop();

  boolean isLongArithmeticBinop();

  boolean isLongLogicalBinop();

  boolean isMultiplication();

  boolean isNewArray();

  boolean isArrayLength();

  boolean isArrayGet();

  boolean isArrayPut();

  boolean isMonitorEnter();

  boolean isMonitorExit();

  boolean isFilledNewArray();

  int size();

  InstructionOffsetSubject getOffset(MethodSubject methodSubject);

  MethodSubject getMethodSubject();

  default int getLineNumber() {
    LineNumberTable lineNumberTable = getMethodSubject().getLineNumberTable();
    return lineNumberTable == null ? -1 : lineNumberTable.getLineForInstruction(this);
  }

  default RetraceMethodResult retrace(Retracer retracer) {
    MethodSubject methodSubject = getMethodSubject();
    assert methodSubject.isPresent();
    return retracer.retraceMethod(methodSubject.asFoundMethodSubject().asMethodReference());
  }

  default RetraceFrameResult retraceLinePosition(Retracer retracer) {
    return retrace(retracer)
        .narrowByPosition(RetraceStackTraceContext.empty(), OptionalInt.of(getLineNumber()));
  }

  default RetraceFrameResult retracePcPosition(Retracer retracer, MethodSubject methodSubject) {
    return retrace(retracer)
        .narrowByPosition(
            RetraceStackTraceContext.empty(), OptionalInt.of(getOffset(methodSubject).offset));
  }
}
