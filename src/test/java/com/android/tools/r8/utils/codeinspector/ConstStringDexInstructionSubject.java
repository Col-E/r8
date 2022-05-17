// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.dex.code.DexConstString;
import com.android.tools.r8.dex.code.DexConstStringJumbo;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.graph.DexString;

public class ConstStringDexInstructionSubject extends DexInstructionSubject
    implements ConstStringInstructionSubject {
  public ConstStringDexInstructionSubject(DexInstruction instruction, MethodSubject method) {
    super(instruction, method);
    assert isConstString(JumboStringMode.ALLOW);
  }

  @Override
  public DexString getString() {
    if (instruction instanceof DexConstString) {
      return ((DexConstString) instruction).getString();
    } else {
      assert (instruction instanceof DexConstStringJumbo);
      return ((DexConstStringJumbo) instruction).getString();
    }
  }
}
