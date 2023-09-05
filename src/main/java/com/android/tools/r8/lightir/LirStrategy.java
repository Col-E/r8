// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.lightir;

import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.NumberGenerator;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Phi.RegisterReadType;
import com.android.tools.r8.ir.code.Value;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntFunction;

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

  public static LirStrategy<Value, Integer> getDefaultStrategy() {
    return new PhiInInstructionsStrategy();
  }

  public abstract LirEncodingStrategy<V, EV> getEncodingStrategy();

  public abstract LirDecodingStrategy<V, EV> getDecodingStrategy(
      LirCode<EV> code, NumberGenerator valueNumberGenerator);

  /**
   * Encoding of a value with a phi-bit.
   *
   * <p>Due to the generic signature the encoding is boxed so this just adds some convenient
   * predicates and formatting since it is boxed anyway.
   *
   * <p>JVM code attribute has length u2 (16-bit / max 65536). Thus, the number of basic blocks and
   * phi count is also bounded by the same value. The encoding here is taken to be
   *
   * <ul>
   *   <li>1-bit for value/phi bit (sign bit / most significant bit),
   *   <li>15-bit phi index (the following most significant bits).
   *   <li>16-bit block index (the least significant bits).
   * </ul>
   *
   * <p>TODO(b/225838009): Fix this encoding to support pathological block counts above 32k.
   */
  public static class PhiOrValue {
    private final int value;

    public static PhiOrValue forPhi(int blockIndex, int phiIndex) {
      int sign = Integer.MIN_VALUE;
      int block = ensure15bit(blockIndex) << 16;
      int phi = ByteUtils.ensureU2(phiIndex);
      int raw = sign | block | phi;
      assert raw < 0;
      return new PhiOrValue(raw);
    }

    private static int ensure15bit(int value) {
      if (value >= (1 << 15)) {
        // TODO(b/225838009): Support 16-bit values and inline this helper.
        throw new Unimplemented("No support for more than 15-bit block index.");
      }
      return ByteUtils.ensureU2(value);
    }

    public static PhiOrValue forNonPhi(int index) {
      assert index >= 0;
      return new PhiOrValue(index);
    }

    private PhiOrValue(int value) {
      this.value = value;
    }

    public boolean isPhi() {
      return value < 0;
    }

    public boolean isNonPhi() {
      return !isPhi();
    }

    public int getRawValue() {
      return value;
    }

    public int getDecodedValue() {
      assert isNonPhi();
      return value;
    }

    public int getBlockIndex() {
      assert isPhi();
      return (value & ~Integer.MIN_VALUE) >> 16;
    }

    public int getPhiIndex() {
      assert isPhi();
      return value & 0xFFFF;
    }

    @Override
    public String toString() {
      if (isPhi()) {
        return "phi(" + getBlockIndex() + "," + getPhiIndex() + ")";
      }
      return "v" + getDecodedValue();
    }

    @Override
    public int hashCode() {
      return value;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null) {
        return false;
      }
      if (obj == this) {
        return true;
      }
      return obj instanceof PhiOrValue && (value == ((PhiOrValue) obj).value);
    }
  }

  public static class ExternalPhisStrategy extends LirStrategy<Value, PhiOrValue> {

    @Override
    public LirEncodingStrategy<Value, PhiOrValue> getEncodingStrategy() {
      return new EncodingStrategy();
    }

    @Override
    public LirDecodingStrategy<Value, PhiOrValue> getDecodingStrategy(
        LirCode<PhiOrValue> code, NumberGenerator valueNumberGenerator) {
      return new DecodingStrategy(code, valueNumberGenerator);
    }

    private static class StrategyInfo extends LirStrategyInfo<PhiOrValue> {
      private static final StrategyInfo EMPTY = new StrategyInfo(new int[0]);

      private final int[] phiTable;

      public StrategyInfo(int[] phiTable) {
        this.phiTable = phiTable;
      }

      @Override
      public LirSsaValueStrategy<PhiOrValue> getReferenceStrategy() {
        return ReferenceStrategy.INSTANCE;
      }
    }

    private static class EncodingStrategy extends LirEncodingStrategy<Value, PhiOrValue> {
      private final Map<Value, PhiOrValue> values = new IdentityHashMap<>();
      private final Reference2IntMap<BasicBlock> blocks = new Reference2IntOpenHashMap<>();
      private final ArrayList<Integer> phiTable = new ArrayList<>();

      @Override
      public boolean isPhiInlineInstruction() {
        return false;
      }

      @Override
      public void defineBlock(BasicBlock block, int index) {
        assert !blocks.containsKey(block);
        blocks.put(block, index);
        if (block.getPhis().isEmpty()) {
          return;
        }
        int i = 0;
        for (Phi phi : block.getPhis()) {
          values.put(phi, PhiOrValue.forPhi(index, i++));
        }
        // Amend the phi table with the index of the basic block and the number of its phis.
        phiTable.add(index);
        phiTable.add(i);
      }

      @Override
      public PhiOrValue defineValue(Value value, int index) {
        if (value.isPhi()) {
          // Phis are defined as part of blocks.
          PhiOrValue encodedValue = values.get(value);
          assert encodedValue != null;
          return encodedValue;
        }
        PhiOrValue encodedValue = PhiOrValue.forNonPhi(index);
        values.put(value, encodedValue);
        return encodedValue;
      }

      @Override
      public boolean verifyValueIndex(Value value, int expectedIndex) {
        PhiOrValue encodedValue = values.get(value);
        assert encodedValue.isNonPhi();
        assert expectedIndex == encodedValue.getDecodedValue();
        return true;
      }

      @Override
      public PhiOrValue getEncodedValue(Value value) {
        return values.get(value);
      }

      @Override
      public int getBlockIndex(BasicBlock block) {
        assert blocks.containsKey(block);
        return blocks.getInt(block);
      }

      @Override
      public LirStrategyInfo<PhiOrValue> getStrategyInfo() {
        if (phiTable.isEmpty()) {
          return StrategyInfo.EMPTY;
        }
        int[] array = new int[phiTable.size()];
        for (int i = 0; i < phiTable.size(); i++) {
          array[i] = phiTable.get(i);
        }
        return new StrategyInfo(array);
      }
    }

    private static class DecodingStrategy extends LirDecodingStrategy<Value, PhiOrValue> {

      private final Value[] values;
      private final int firstPhiValueIndex;

      DecodingStrategy(LirCode<PhiOrValue> code, NumberGenerator valueNumberGenerator) {
        super(valueNumberGenerator);
        values = new Value[code.getArgumentCount() + code.getInstructionCount()];
        int phiValueIndex = -1;
        for (LirInstructionView view : code) {
          if (view.getOpcode() == LirOpcodes.PHI) {
            phiValueIndex = code.getArgumentCount() + view.getInstructionIndex();
            break;
          }
        }
        this.firstPhiValueIndex = phiValueIndex;
        reserveValueIndexes(values.length);
      }

      private int decode(PhiOrValue encodedValue, LirStrategyInfo<PhiOrValue> strategyInfo) {
        if (encodedValue.isNonPhi()) {
          return encodedValue.getDecodedValue();
        }
        StrategyInfo info = (StrategyInfo) strategyInfo;
        int phiBlock = encodedValue.getBlockIndex();
        int phiIndex = encodedValue.getPhiIndex();
        assert firstPhiValueIndex != -1;
        int index = firstPhiValueIndex;
        for (int i = 0; i < info.phiTable.length; i++) {
          int blockIndex = info.phiTable[i];
          if (blockIndex == phiBlock) {
            return index + phiIndex;
          }
          index += info.phiTable[++i];
        }
        throw new Unreachable("Unexpectedly fell off the end of the phi table");
      }

      @Override
      public Value getValue(PhiOrValue encodedValue, LirStrategyInfo<PhiOrValue> strategyInfo) {
        int index = decode(encodedValue, strategyInfo);
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
      @SuppressWarnings("ReferenceEquality")
      public Value getValueDefinitionForInstructionIndex(
          int index, TypeElement type, Function<PhiOrValue, DebugLocalInfo> getLocalInfo) {
        PhiOrValue encodedValue = new PhiOrValue(index);
        assert encodedValue.isNonPhi();
        DebugLocalInfo localInfo = getLocalInfo.apply(encodedValue);
        Value value = values[index];
        if (value == null) {
          value = new Value(getValueNumber(index), type, localInfo);
          values[index] = value;
        } else {
          value.setType(type);
          if (localInfo != null && !value.hasLocalInfo()) {
            value.setLocalInfo(localInfo);
          }
          assert localInfo == value.getLocalInfo();
        }
        return value;
      }

      @Override
      public Phi getPhiDefinitionForInstructionIndex(
          int valueIndex,
          IntFunction<BasicBlock> getBlock,
          TypeElement type,
          Function<PhiOrValue, DebugLocalInfo> getLocalInfo,
          LirStrategyInfo<PhiOrValue> strategyInfo) {
        PhiOrValue encodedValue = getEncodedPhiForAbsoluteValueIndex(valueIndex, strategyInfo);
        BasicBlock block = getBlock.apply(encodedValue.getBlockIndex());
        DebugLocalInfo localInfo = getLocalInfo.apply(encodedValue);
        Phi phi =
            new Phi(getValueNumber(valueIndex), block, type, localInfo, RegisterReadType.NORMAL);
        Value value = values[valueIndex];
        if (value != null) {
          // A fake ssa value has already been created, replace the users by the actual phi.
          // TODO(b/225838009): We could consider encoding the value phi-bit in the value index
          //  and avoid the overhead of replacing users at phi-definition time.
          assert !value.isPhi();
          value.replaceUsers(phi);
        }
        values[valueIndex] = phi;
        return phi;
      }

      private PhiOrValue getEncodedPhiForAbsoluteValueIndex(
          int phiValueIndex, LirStrategyInfo<PhiOrValue> strategyInfo) {
        StrategyInfo info = (StrategyInfo) strategyInfo;
        int currentPhiValueIndex = firstPhiValueIndex;
        for (int i = 0; i < info.phiTable.length; i += 2) {
          assert currentPhiValueIndex <= phiValueIndex;
          int blockIndex = info.phiTable[i];
          int phiCount = info.phiTable[i + 1];
          assert phiCount > 0;
          if (phiValueIndex < currentPhiValueIndex + phiCount) {
            int phiOffsetInBlock = phiValueIndex - currentPhiValueIndex;
            return PhiOrValue.forPhi(blockIndex, phiOffsetInBlock);
          }
          currentPhiValueIndex += phiCount;
        }
        throw new Unreachable("Unexpected fall off the end of the phi table");
      }
    }

    // TODO(b/225838009): Consider still encoding the local value refs as small relative indexes.
    private static class ReferenceStrategy extends LirSsaValueStrategy<PhiOrValue> {

      private static final ReferenceStrategy INSTANCE = new ReferenceStrategy();

      @Override
      public int encodeValueIndex(PhiOrValue value, int currentValueIndex) {
        return value.getRawValue();
      }

      @Override
      public PhiOrValue decodeValueIndex(int encodedValueIndex, int currentValueIndex) {
        return new PhiOrValue(encodedValueIndex);
      }
    }
  }

}
