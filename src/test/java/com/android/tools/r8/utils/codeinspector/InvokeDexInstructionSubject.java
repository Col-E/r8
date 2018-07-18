// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.code.Instruction;
import com.android.tools.r8.graph.DexMethod;

public class InvokeDexInstructionSubject extends DexInstructionSubject
    implements InvokeInstructionSubject {

  private final CodeInspector codeInspector;

  public InvokeDexInstructionSubject(CodeInspector codeInspector, Instruction instruction) {
    super(instruction);
    this.codeInspector = codeInspector;
    assert isInvoke();
  }

  @Override
  public TypeSubject holder() {
    return new TypeSubject(codeInspector, invokedMethod().getHolder());
  }

  @Override
  public DexMethod invokedMethod() {
    return instruction.getMethod();
  }
}
