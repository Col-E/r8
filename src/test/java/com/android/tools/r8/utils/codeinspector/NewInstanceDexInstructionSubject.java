// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.dex.code.DexNewInstance;
import com.android.tools.r8.graph.DexType;

public class NewInstanceDexInstructionSubject extends DexInstructionSubject
    implements NewInstanceInstructionSubject {
  public NewInstanceDexInstructionSubject(DexInstruction instruction, MethodSubject method) {
    super(instruction, method);
  }

  @Override
  public DexType getType() {
    return ((DexNewInstance) instruction).getType();
  }
}
