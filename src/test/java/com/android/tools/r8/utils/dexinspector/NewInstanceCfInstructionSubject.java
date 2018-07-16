// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.dexinspector;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfNew;
import com.android.tools.r8.graph.DexType;

public class NewInstanceCfInstructionSubject extends CfInstructionSubject
    implements NewInstanceInstructionSubject {
  public NewInstanceCfInstructionSubject(CfInstruction instruction) {
    super(instruction);
  }

  @Override
  public DexType getType() {
    return ((CfNew) instruction).getType();
  }
}
