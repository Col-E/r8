// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.retrace.RetraceApi;
import com.android.tools.r8.retrace.RetraceMethodResult;

public interface InstructionSubject {

  enum JumboStringMode {
    ALLOW,
    DISALLOW
  };

  DexInstructionSubject asDexInstruction();

  CfInstructionSubject asCfInstruction();

  boolean isFieldAccess();

  boolean isInstancePut();

  boolean isStaticPut();

  boolean isInstanceGet();

  boolean isStaticGet();

  DexField getField();

  boolean isInvoke();

  boolean isInvokeVirtual();

  boolean isInvokeInterface();

  boolean isInvokeStatic();

  boolean isInvokeSpecial();

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

  boolean isJumboString();

  long getConstNumber();

  String getConstString();

  boolean isConstClass();

  boolean isConstClass(String type);

  boolean isGoto();

  boolean isIfNez();

  boolean isIfEqz();

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

  boolean isPackedSwitch();

  boolean isSparseSwitch();

  boolean isMultiplication();

  boolean isNewArray();

  boolean isArrayLength();

  boolean isArrayPut();

  boolean isMonitorEnter();

  boolean isMonitorExit();

  int size();

  InstructionOffsetSubject getOffset(MethodSubject methodSubject);

  MethodSubject getMethodSubject();

  default int getLineNumber() {
    LineNumberTable lineNumberTable = getMethodSubject().getLineNumberTable();
    return lineNumberTable == null ? -1 : lineNumberTable.getLineForInstruction(this);
  }

  default RetraceMethodResult retrace(RetraceApi retracer) {
    MethodSubject methodSubject = getMethodSubject();
    assert methodSubject.isPresent();
    return retracer.retrace(methodSubject.asFoundMethodSubject().asMethodReference());
  }

  default RetraceMethodResult retraceLinePosition(RetraceApi retracer) {
    return retrace(retracer).narrowByLine(getLineNumber());
  }

  default RetraceMethodResult retracePcPosition(RetraceApi retracer, MethodSubject methodSubject) {
    return retrace(retracer).narrowByLine(getOffset(methodSubject).offset);
  }
}
