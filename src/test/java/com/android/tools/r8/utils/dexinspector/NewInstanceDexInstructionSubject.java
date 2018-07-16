// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.dexinspector;

import com.android.tools.r8.code.Instruction;
import com.android.tools.r8.code.NewInstance;
import com.android.tools.r8.graph.DexType;

public class NewInstanceDexInstructionSubject extends DexInstructionSubject
    implements NewInstanceInstructionSubject {
  public NewInstanceDexInstructionSubject(Instruction instruction) {
    super(instruction);
  }

  @Override
  public DexType getType() {
    return ((NewInstance) instruction).getType();
  }
}
