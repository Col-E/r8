// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.cf.code.CfTryCatch;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.Code;
import java.util.Iterator;

class CfTryCatchIterator implements TryCatchIterator {
  private final CodeInspector codeInspector;
  private final CfCode cfCode;
  private final Iterator<CfTryCatch> iterator;

  CfTryCatchIterator(CodeInspector codeInspector, MethodSubject methodSubject) {
    this.codeInspector = codeInspector;
    assert methodSubject.isPresent();
    Code code = methodSubject.getMethod().getCode();
    assert code != null && code.isCfCode();
    cfCode = code.asCfCode();
    iterator = cfCode.getTryCatchRanges().iterator();
  }

  @Override
  public boolean hasNext() {
    return iterator.hasNext();
  }

  @Override
  public TryCatchSubject next() {
    return codeInspector.createTryCatchSubject(cfCode, iterator.next());
  }

}
