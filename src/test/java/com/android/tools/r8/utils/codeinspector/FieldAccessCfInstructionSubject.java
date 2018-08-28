// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.cf.code.CfFieldInstruction;
import com.android.tools.r8.cf.code.CfInstruction;

public class FieldAccessCfInstructionSubject extends CfInstructionSubject
    implements FieldAccessInstructionSubject {
  private final CodeInspector codeInspector;

  public FieldAccessCfInstructionSubject(CodeInspector codeInspector, CfInstruction instruction) {
    super(instruction);
    this.codeInspector = codeInspector;
    assert isFieldAccess();
  }

  @Override
  public TypeSubject holder() {
    return new TypeSubject(codeInspector, ((CfFieldInstruction) instruction).getField().getHolder());
  }

  @Override
  public String name() {
    return ((CfFieldInstruction) instruction).getField().name.toString();
  }
}
