// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.codeinspector;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfTryCatch;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;

class CfTryCatchSubject implements TryCatchSubject {
  private final CfCode cfCode;
  private final CfTryCatch tryCatch;

  CfTryCatchSubject(CfCode cfCode, CfTryCatch tryCatch) {
    this.cfCode = cfCode;
    this.tryCatch = tryCatch;
  }

  @Override
  public RangeSubject getRange() {
    int index = 0;
    int startIndex = -1;
    int endIndex = -1;
    for (CfInstruction instruction : cfCode.instructions) {
      if (startIndex < 0 && instruction.equals(tryCatch.start)) {
        startIndex = index;
      }
      if (endIndex < 0 && instruction.equals(tryCatch.end)) {
        // To be inclusive, increase the index so that the range includes the current instruction.
        assertNotEquals(-1, startIndex);
        index++;
        endIndex = index;
        break;
      }
      index++;
    }
    assertNotEquals(-1, startIndex);
    assertNotEquals(-1, endIndex);
    assertTrue(startIndex < endIndex);
    return new RangeSubject(startIndex, endIndex);
  }

  @Override
  public boolean isCatching(String exceptionType) {
    for (DexType guardType : tryCatch.guards) {
      if (guardType.toString().equals(exceptionType)
        || guardType.toDescriptorString().equals(exceptionType)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean hasCatchAll() {
    return isCatching(DexItemFactory.catchAllType.toDescriptorString());
  }

}
