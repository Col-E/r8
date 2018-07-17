// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.dexinspector;

import com.android.tools.r8.code.ConstString;
import com.android.tools.r8.code.ConstStringJumbo;
import com.android.tools.r8.code.Instruction;
import com.android.tools.r8.graph.DexString;

public class ConstStringDexInstructionSubject extends DexInstructionSubject
    implements ConstStringInstructionSubject {
  public ConstStringDexInstructionSubject(Instruction instruction) {
    super(instruction);
    assert isConstString(JumboStringMode.ALLOW);
  }

  @Override
  public DexString getString() {
    if (instruction instanceof ConstString) {
      return ((ConstString) instruction).getString();
    } else {
      assert (instruction instanceof ConstStringJumbo);
      return ((ConstStringJumbo) instruction).getString();
    }
  }
}
