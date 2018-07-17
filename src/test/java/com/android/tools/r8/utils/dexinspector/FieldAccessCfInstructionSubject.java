// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.dexinspector;

import com.android.tools.r8.cf.code.CfFieldInstruction;
import com.android.tools.r8.cf.code.CfInstruction;

public class FieldAccessCfInstructionSubject extends CfInstructionSubject
    implements FieldAccessInstructionSubject {
  private final DexInspector dexInspector;

  public FieldAccessCfInstructionSubject(DexInspector dexInspector, CfInstruction instruction) {
    super(instruction);
    this.dexInspector = dexInspector;
    assert isFieldAccess();
  }

  @Override
  public TypeSubject holder() {
    return new TypeSubject(dexInspector, ((CfFieldInstruction) instruction).getField().getHolder());
  }
}
