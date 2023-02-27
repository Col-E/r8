// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.lightir;

import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Phi.RegisterReadType;
import com.android.tools.r8.ir.code.Value;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;

/**
 * Abstraction for encoding and decoding LIR values.
 *
 * @param <V> Abstract type of high-level values. This is always 'Value' but templating it avoids
 *     any use of the actual high-level IR Value type.
 * @param <EV> Abstract type of encoded values. This is an intermediate encoding prior to
 *     serializing the value to an int or sequence of bytes. This encoding allows abstracting out
 *     the possible encoding for phi and non-phi values.
 */
public abstract class LirStrategy<V, EV> {
  public abstract LirEncodingStrategy<V, EV> getEncodingStrategy();

  public abstract LirDecodingStrategy<V, EV> getDecodingStrategy(LirCode<EV> code);

  // Strategy that implements the encoding of phi values as instructions in the LIR instruction
  // stream.
  public static class PhiInInstructionsStrategy extends LirStrategy<Value, Integer> {

    @Override
    public LirEncodingStrategy<Value, Integer> getEncodingStrategy() {
      return new EncodingStrategy();
    }

    @Override
    public LirDecodingStrategy<Value, Integer> getDecodingStrategy(LirCode<Integer> code) {
      return new DecodingStrategy(code);
    }
  }

  private static class EncodingStrategy extends LirEncodingStrategy<Value, Integer> {

    // EV == Integer and its definition is equal to its shifted instruction index.
    // The conversion for EV to its int-valued reference is determined by the 'valueStrategy'.

    private final LirSsaValueStrategy<Integer> valueStrategy = LirSsaValueStrategy.get();
    private final Reference2IntMap<Value> values = new Reference2IntOpenHashMap<>();
    private final Reference2IntMap<BasicBlock> blocks = new Reference2IntOpenHashMap<>();

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
    public LirSsaValueStrategy<Integer> getSsaValueStrategy() {
      return valueStrategy;
    }
  }

  private static class DecodingStrategy extends LirDecodingStrategy<Value, Integer> {

    private final Value[] values;

    DecodingStrategy(LirCode<Integer> code) {
      values = new Value[code.getArgumentCount() + code.getInstructionCount()];
    }

    @Override
    public Value getValue(Integer encodedValue) {
      int index = encodedValue;
      Value value = values[index];
      if (value == null) {
        value = new Value(index, TypeElement.getBottom(), null);
        values[index] = value;
      }
      return value;
    }

    @Override
    public Value getValueDefinitionForInstructionIndex(
        int index, TypeElement type, DebugLocalInfo localInfo) {
      Value value = values[index];
      if (value == null) {
        value = new Value(index, type, localInfo);
        values[index] = value;
      } else {
        value.setType(type);
        if (localInfo != null) {
          value.setLocalInfo(localInfo);
        }
      }
      return value;
    }

    @Override
    public Phi getPhiDefinitionForInstructionIndex(
        int index, BasicBlock block, TypeElement type, DebugLocalInfo localInfo) {
      Phi phi = new Phi(index, block, type, localInfo, RegisterReadType.NORMAL);
      Value value = values[index];
      if (value != null) {
        // A fake ssa value has already been created, replace the users by the actual phi.
        // TODO(b/225838009): We could consider encoding the value type as a bit in the value index
        //  and avoid the overhead of replacing users at phi-definition time.
        assert !value.isPhi();
        value.replaceUsers(phi);
      }
      values[index] = phi;
      return phi;
    }
  }
}
