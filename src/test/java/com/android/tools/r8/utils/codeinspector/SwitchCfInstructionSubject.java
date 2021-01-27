// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfSwitch;
import java.util.List;

public class SwitchCfInstructionSubject extends CfInstructionSubject
    implements SwitchInstructionSubject {
  public SwitchCfInstructionSubject(CfInstruction instruction, MethodSubject method) {
    super(instruction, method);
    assert isSwitch();
  }

  @Override
  public List<Integer> getKeys() {
    return ((CfSwitch) instruction).getKeys();
  }

  @Override
  public SwitchInstructionSubject asSwitch() {
    return this;
  }
}
