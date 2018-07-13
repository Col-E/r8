// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.dexinspector;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.graph.Code;
import java.util.Iterator;

class CfInstructionIterator implements InstructionIterator {

  private final DexInspector dexInspector;
  private final Iterator<CfInstruction> iterator;

  CfInstructionIterator(DexInspector dexInspector, MethodSubject method) {
    this.dexInspector = dexInspector;
    assert method.isPresent();
    Code code = method.getMethod().getCode();
    assert code != null && code.isCfCode();
    iterator = code.asCfCode().getInstructions().iterator();
  }

  @Override
  public boolean hasNext() {
    return iterator.hasNext();
  }

  @Override
  public InstructionSubject next() {
    return dexInspector.createInstructionSubject(iterator.next());
  }
}
