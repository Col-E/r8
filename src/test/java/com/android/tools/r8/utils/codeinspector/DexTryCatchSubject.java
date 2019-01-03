// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.codeinspector;

import static com.android.tools.r8.graph.DexCode.TryHandler.NO_HANDLER;

import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexCode.Try;
import com.android.tools.r8.graph.DexCode.TryHandler;
import com.android.tools.r8.graph.DexCode.TryHandler.TypeAddrPair;

class DexTryCatchSubject implements TryCatchSubject {
  private final DexCode dexCode;
  private final Try tryElement;
  private final TryHandler tryHandler;

  DexTryCatchSubject(DexCode dexCode, Try tryElement, TryHandler tryHandler) {
    this.dexCode = dexCode;
    this.tryElement = tryElement;
    this.tryHandler = tryHandler;
  }

  @Override
  public RangeSubject getRange() {
    return new RangeSubject(
        tryElement.startAddress,
        tryElement.startAddress + tryElement.instructionCount - 1);
  }

  @Override
  public boolean isCatching(String exceptionType) {
    for (TypeAddrPair pair : tryHandler.pairs) {
      if (pair.type.toString().equals(exceptionType)
        || pair.type.toDescriptorString().equals(exceptionType)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean hasCatchAll() {
    return tryHandler.catchAllAddr != NO_HANDLER;
  }

}
