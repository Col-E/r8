// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.graph.DexMethod;

public class InvokeCfInstructionSubject extends CfInstructionSubject
    implements InvokeInstructionSubject {
  private final CodeInspector codeInspector;

  public InvokeCfInstructionSubject(CodeInspector codeInspector, CfInstruction instruction) {
    super(instruction);
    assert isInvoke();
    this.codeInspector = codeInspector;
  }

  @Override
  public TypeSubject holder() {
    return new TypeSubject(codeInspector, invokedMethod().getHolder());
  }

  @Override
  public DexMethod invokedMethod() {
    if (isInvokeDynamic()) {
      throw new Unimplemented(
          "invokeMethod is not implemented for the INVOKEDYNAMIC CF instruction.");
    }
    return ((CfInvoke) instruction).getMethod();
  }
}
