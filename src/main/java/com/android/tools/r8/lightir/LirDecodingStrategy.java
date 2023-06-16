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

  private final NumberGenerator valueNumberGenerator;

  public LirDecodingStrategy(NumberGenerator valueNumberGenerator) {
    this.valueNumberGenerator = valueNumberGenerator;
  }

  public final int getValueNumber(int encodedValueIndex) {
    return valueNumberGenerator == null ? encodedValueIndex : valueNumberGenerator.next();
  }

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
