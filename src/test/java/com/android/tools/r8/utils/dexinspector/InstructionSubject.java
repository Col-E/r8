// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.dexinspector;

public interface InstructionSubject {
  boolean isFieldAccess();

  boolean isInvokeVirtual();

  boolean isInvokeInterface();

  boolean isInvokeStatic();

  boolean isNop();

  boolean isConstString();

  boolean isConstString(String value);

  boolean isGoto();

  boolean isIfNez();

  boolean isIfEqz();

  boolean isReturnVoid();

  boolean isThrow();

  boolean isInvoke();

  boolean isNewInstance();
}
