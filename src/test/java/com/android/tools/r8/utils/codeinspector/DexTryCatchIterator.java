// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexCode;
import java.util.NoSuchElementException;

class DexTryCatchIterator implements TryCatchIterator {
  private final CodeInspector codeInspector;
  private final DexCode code;
  private int index;

  DexTryCatchIterator(CodeInspector codeInspector, MethodSubject methodSubject) {
    this.codeInspector = codeInspector;
    assert methodSubject.isPresent();
    Code code = methodSubject.getMethod().getCode();
    assert code != null && code.isDexCode();
    this.code = code.asDexCode();
    this.index = 0;
  }

  @Override
  public boolean hasNext() {
    return index < code.tries.length;
  }

  @Override
  public TryCatchSubject next() {
    if (index == code.tries.length) {
      throw new NoSuchElementException();
    }
    int current = index++;
    return codeInspector.createTryCatchSubject(
        code, code.tries[current], code.handlers[code.tries[current].handlerIndex]);
  }
}
