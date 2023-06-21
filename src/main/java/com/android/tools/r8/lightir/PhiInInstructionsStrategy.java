// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.lightir;

import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.NumberGenerator;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Phi.RegisterReadType;
import com.android.tools.r8.ir.code.Value;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.function.Function;
import java.util.function.IntFunction;

/** Strategy encoding phi values as instructions in the LIR instruction stream. */
public class PhiInInstructionsStrategy extends LirStrategy<Value, Integer> {

  @Override
  public LirEncodingStrategy<Value, Integer> getEncodingStrategy() {
    return new EncodingStrategy();
  }

  @Override
  public LirDecodingStrategy<Value, Integer> getDecodingStrategy(
      LirCode<Integer> code, NumberGenerator valueNumberGenerator) {
    return new DecodingStrategy(code, valueNumberGenerator);
  }

  private static class EncodingStrategy extends LirEncodingStrategy<Value, Integer> {

    // EV == Integer and its definition is equal to its shifted instruction index.
    // The conversion for EV to its int-valued reference is determined by the 'valueStrategy'.

    private final LirSsaValueStrategy<Integer> referenceStrategy = LirSsaValueStrategy.get();
    private final Reference2IntMap<Value> values = new Reference2IntOpenHashMap<>();
    private final Reference2IntMap<BasicBlock> blocks = new Reference2IntOpenHashMap<>();

    @Override
    public boolean isPhiInlineInstruction() {
      return true;
    }

    @Override
    public void defineBlock(BasicBlock block, int index) {
      assert !blocks.containsKey(block);
      blocks.put(block, index);
    }

    @Override
    public Integer defineValue(Value value, int index) {
      values.put(value, index);
      return index;
    }

    @Override
    public boolean verifyValueIndex(Value value, int expectedIndex) {
      assert expectedIndex == values.getInt(value);
      return true;
    }

    @Override
    public Integer getEncodedValue(Value value) {
      return values.getInt(value);
    }

    @Override
    public int getBlockIndex(BasicBlock block) {
      assert blocks.containsKey(block);
      return blocks.getInt(block);
    }

    @Override
    public LirStrategyInfo<Integer> getStrategyInfo() {
      return new StrategyInfo(referenceStrategy);
    }
  }

  private static class StrategyInfo extends LirStrategyInfo<Integer> {

    private final LirSsaValueStrategy<Integer> referenceStrategy;

    public StrategyInfo(LirSsaValueStrategy<Integer> referenceStrategy) {
      this.referenceStrategy = referenceStrategy;
    }

    @Override
    public LirSsaValueStrategy<Integer> getReferenceStrategy() {
      return referenceStrategy;
    }
  }

  private static class DecodingStrategy extends LirDecodingStrategy<Value, Integer> {

    private final Value[] values;

    DecodingStrategy(LirCode<Integer> code, NumberGenerator valueNumberGenerator) {
      super(valueNumberGenerator);
      values = new Value[code.getArgumentCount() + code.getInstructionCount()];
      reserveValueIndexes(values.length);
    }

    @Override
    public Value getValue(Integer encodedValue, LirStrategyInfo<Integer> strategyInfo) {
      int index = encodedValue;
      Value value = values[index];
      if (value == null) {
        value = new Value(getValueNumber(index), TypeElement.getBottom(), null);
        values[index] = value;
      }
      return value;
    }

    @Override
    Value internalGetFreshUnusedValue(int valueNumber, TypeElement type) {
      return new Value(valueNumber, type, null);
    }

    @Override
    public Value getValueDefinitionForInstructionIndex(
        int index, TypeElement type, Function<Integer, DebugLocalInfo> getLocalInfo) {
      DebugLocalInfo localInfo = getLocalInfo.apply(index);
      Value value = values[index];
      if (value == null) {
        value = new Value(getValueNumber(index), type, localInfo);
        values[index] = value;
      } else {
        value.setType(type);
        if (localInfo != null) {
          if (!value.hasLocalInfo()) {
            value.setLocalInfo(localInfo);
          }
          assert localInfo == value.getLocalInfo();
        }
      }
      return value;
    }

    @Override
    public Phi getPhiDefinitionForInstructionIndex(
        int valueIndex,
        IntFunction<BasicBlock> getBlock,
        TypeElement type,
        Function<Integer, DebugLocalInfo> getLocalInfo,
        LirStrategyInfo<Integer> strategyInfo) {
      BasicBlock block = getBlock.apply(valueIndex);
      DebugLocalInfo localInfo = getLocalInfo.apply(valueIndex);
      Phi phi =
          new Phi(getValueNumber(valueIndex), block, type, localInfo, RegisterReadType.NORMAL);
      Value value = values[valueIndex];
      if (value != null) {
        // A fake ssa value has already been created, replace the users by the actual phi.
        // TODO(b/225838009): We could consider encoding the value type as a bit in the value index
        //  and avoid the overhead of replacing users at phi-definition time.
        assert !value.isPhi();
        value.replaceUsers(phi);
      }
      values[valueIndex] = phi;
      return phi;
    }
  }
}
