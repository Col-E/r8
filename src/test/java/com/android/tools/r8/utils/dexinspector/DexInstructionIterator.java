// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.dexinspector;

import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexCode;
import java.util.NoSuchElementException;

class DexInstructionIterator implements InstructionIterator {

  private DexInspector dexInspector;
  private final DexCode code;
  private int index;

  DexInstructionIterator(DexInspector dexInspector, MethodSubject method) {
    this.dexInspector = dexInspector;
    assert method.isPresent();
    Code code = method.getMethod().getCode();
    assert code != null && code.isDexCode();
    this.code = code.asDexCode();
    this.index = 0;
  }

  @Override
  public boolean hasNext() {
    return index < code.instructions.length;
  }

  @Override
  public InstructionSubject next() {
    if (index == code.instructions.length) {
      throw new NoSuchElementException();
    }
    return dexInspector.createInstructionSubject(code.instructions[index++]);
  }
}
