// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.cf.code.CfCheckCast;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.graph.DexType;

public class CheckCastCfInstructionSubject extends CfInstructionSubject
    implements CheckCastInstructionSubject {
  public CheckCastCfInstructionSubject(CfInstruction instruction, MethodSubject method) {
    super(instruction, method);
    assert isCheckCast();
  }

  @Override
  public DexType getType() {
    return ((CfCheckCast) instruction).getType();
  }

  @Override
  public CheckCastInstructionSubject asCheckCast() {
    return this;
  }
}
