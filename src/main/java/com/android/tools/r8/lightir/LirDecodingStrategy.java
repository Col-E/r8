// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.lightir;

import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.NumberGenerator;
import com.android.tools.r8.ir.code.Phi;
import java.util.function.Function;
import java.util.function.IntFunction;

/** Abstraction for how to decode SSA values (and basic blocks) when reading LIR. */
public abstract class LirDecodingStrategy<V, EV> {

  private final boolean useValueIndex;
  private final NumberGenerator valueNumberGenerator;

  public LirDecodingStrategy(NumberGenerator valueNumberGenerator) {
    assert valueNumberGenerator != null;
    this.useValueIndex = valueNumberGenerator.peek() == 0;
    this.valueNumberGenerator = valueNumberGenerator;
  }

  void reserveValueIndexes(int valuesCount) {
    if (useValueIndex) {
      for (int i = 0; i < valuesCount; i++) {
        valueNumberGenerator.next();
      }
    }
  }

  public NumberGenerator getValueNumberGenerator() {
    return valueNumberGenerator;
  }

  public final int getValueNumber(int encodedValueIndex) {
    if (useValueIndex) {
      assert encodedValueIndex < valueNumberGenerator.peek();
      return encodedValueIndex;
    }
    return valueNumberGenerator.next();
  }

  public final V getFreshUnusedValue(TypeElement type) {
    return internalGetFreshUnusedValue(valueNumberGenerator.next(), type);
  }

  abstract V internalGetFreshUnusedValue(int valueNumber, TypeElement type);

  public abstract V getValue(EV encodedValue, LirStrategyInfo<EV> strategyInfo);

  public abstract V getValueDefinitionForInstructionIndex(
      int instructionIndex, TypeElement type, Function<EV, DebugLocalInfo> getLocalInfo);

  public abstract Phi getPhiDefinitionForInstructionIndex(
      int valueIndex,
      IntFunction<BasicBlock> getBlock,
      TypeElement type,
      Function<EV, DebugLocalInfo> getLocalInfo,
      LirStrategyInfo<EV> strategyInfo);
}
