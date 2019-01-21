// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;

public interface InstructionSubject {

  enum JumboStringMode {
    ALLOW,
    DISALLOW
  };

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

  DexMethod getMethod();

  boolean isNop();

  boolean isConstNumber();

  boolean isConstNumber(long value);

  boolean isConstNull();

  boolean isConstString(JumboStringMode jumboStringMode);

  boolean isConstString(String value, JumboStringMode jumboStringMode);

  boolean isConstClass();

  boolean isConstClass(String type);

  boolean isGoto();

  boolean isIfNez();

  boolean isIfEqz();

  boolean isReturnVoid();

  boolean isReturnObject();

  boolean isThrow();

  boolean isNewInstance();

  boolean isCheckCast();

  boolean isCheckCast(String type);

  boolean isInstanceOf();

  boolean isIf(); // Also include CF/if_cmp* instructions.

  boolean isPackedSwitch();

  boolean isSparseSwitch();

  boolean isMultiplication();

  boolean isNewArray();

  boolean isMonitorEnter();

  boolean isMonitorExit();

  int size();

  InstructionOffsetSubject getOffset(MethodSubject methodSubject);
}
