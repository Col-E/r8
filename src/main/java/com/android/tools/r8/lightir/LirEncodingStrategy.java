// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.lightir;

import com.android.tools.r8.ir.code.BasicBlock;

/** Abstraction for how to encode SSA values (and basic blocks) when building LIR. */
public abstract class LirEncodingStrategy<V, EV> {

  public abstract void defineBlock(BasicBlock block, int index);

  public abstract int getBlockIndex(BasicBlock block);

  public abstract EV defineValue(V value, int index);

  public abstract boolean isPhiInlineInstruction();

  public abstract boolean verifyValueIndex(V value, int expectedIndex);

  public abstract EV getEncodedValue(V value);

  public int getEncodedValueIndexForReference(EV encodedValue, int referencingValueIndex) {
    return getStrategyInfo()
        .getReferenceStrategy()
        .encodeValueIndex(encodedValue, referencingValueIndex);
  }

  public abstract LirStrategyInfo<EV> getStrategyInfo();
}
