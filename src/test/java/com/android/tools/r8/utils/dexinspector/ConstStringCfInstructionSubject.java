// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.dexinspector;

import com.android.tools.r8.cf.code.CfConstString;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.graph.DexString;

public class ConstStringCfInstructionSubject extends CfInstructionSubject
    implements ConstStringInstructionSubject {
  public ConstStringCfInstructionSubject(CfInstruction instruction) {
    super(instruction);
    assert isConstString(JumboStringMode.ALLOW);
  }

  @Override
  public DexString getString() {
    return ((CfConstString) instruction).getString();
  }
}
