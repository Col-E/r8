// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.lightir;

import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.graph.DexItem;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.code.CatchHandlers;
import com.android.tools.r8.ir.code.IRMetadata;
import com.android.tools.r8.ir.code.Position;
import com.google.common.collect.ImmutableMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public class LirCode<EV> implements Iterable<LirInstructionView> {

  public static class PositionEntry {
    final int fromInstructionIndex;
    final Position position;

    public PositionEntry(int fromInstructionIndex, Position position) {
      this.fromInstructionIndex = fromInstructionIndex;
      this.position = position;
    }
  }

  public static class TryCatchTable {
    final Int2ReferenceMap<CatchHandlers<Integer>> tryCatchHandlers;

    public TryCatchTable(Int2ReferenceMap<CatchHandlers<Integer>> tryCatchHandlers) {
      assert !tryCatchHandlers.isEmpty();
      // Copy the map to ensure it has not over-allocated the backing store.
      this.tryCatchHandlers = new Int2ReferenceOpenHashMap<>(tryCatchHandlers);
    }

    public CatchHandlers<Integer> getHandlersForBlock(int blockIndex) {
      return tryCatchHandlers.get(blockIndex);
    }
  }

  public static class DebugLocalInfoTable<EV> {
    private final Map<EV, DebugLocalInfo> valueToLocalMap;
    private final Int2ReferenceMap<int[]> instructionToEndUseMap;

    public DebugLocalInfoTable(
        Map<EV, DebugLocalInfo> valueToLocalMap, Int2ReferenceMap<int[]> instructionToEndUseMap) {
      assert !valueToLocalMap.isEmpty();
      // TODO(b/283049198): Debug ends may not be maintained so we can't assume they are non-empty.
      // Copy the maps to ensure they have not over-allocated the backing store.
      this.valueToLocalMap = ImmutableMap.copyOf(valueToLocalMap);
      this.instructionToEndUseMap =
          instructionToEndUseMap.isEmpty()
              ? null
              : new Int2ReferenceOpenHashMap<>(instructionToEndUseMap);
    }

    public int[] getEnds(int index) {
      if (instructionToEndUseMap == null) {
        return null;
      }
      return instructionToEndUseMap.get(index);
    }

    public void forEachLocalDefinition(BiConsumer<EV, DebugLocalInfo> fn) {
      valueToLocalMap.forEach(fn);
    }
  }

  private final LirStrategyInfo<EV> strategyInfo;

  private final IRMetadata metadata;

  /** Constant pool of items. */
  private final DexItem[] constants;

  private final PositionEntry[] positionTable;

  /** Full number of arguments (including receiver for non-static methods). */
  private final int argumentCount;

  /** Byte encoding of the instructions (excludes arguments, includes phis). */
  private final byte[] instructions;

  /** Cached value for the number of logical instructions (excludes arguments, includes phis). */
  private final int instructionCount;

  /** Table of try-catch handlers for each basic block (if present). */
  private final TryCatchTable tryCatchTable;

  /** Table of debug local information for each SSA value (if present). */
  private final DebugLocalInfoTable<EV> debugLocalInfoTable;

  public static <V, EV> LirBuilder<V, EV> builder(
      DexMethod method, LirEncodingStrategy<V, EV> strategy, DexItemFactory factory) {
    return new LirBuilder<>(method, strategy, factory);
  }

  /** Should be constructed using {@link LirBuilder}. */
  LirCode(
      IRMetadata metadata,
      DexItem[] constants,
      PositionEntry[] positions,
      int argumentCount,
      byte[] instructions,
      int instructionCount,
      TryCatchTable tryCatchTable,
      DebugLocalInfoTable<EV> debugLocalInfoTable,
      LirStrategyInfo<EV> strategyInfo) {
    this.metadata = metadata;
    this.constants = constants;
    this.positionTable = positions;
    this.argumentCount = argumentCount;
    this.instructions = instructions;
    this.instructionCount = instructionCount;
    this.tryCatchTable = tryCatchTable;
    this.debugLocalInfoTable = debugLocalInfoTable;
    this.strategyInfo = strategyInfo;
  }

  public EV decodeValueIndex(int encodedValueIndex, int currentValueIndex) {
    return strategyInfo
        .getReferenceStrategy()
        .decodeValueIndex(encodedValueIndex, currentValueIndex);
  }

  public LirStrategyInfo<EV> getStrategyInfo() {
    return strategyInfo;
  }

  public int getArgumentCount() {
    return argumentCount;
  }

  public byte[] getInstructionBytes() {
    return instructions;
  }

  public int getInstructionCount() {
    return instructionCount;
  }

  public IRMetadata getMetadata() {
    return metadata;
  }

  public DexItem getConstantItem(int index) {
    return constants[index];
  }

  public PositionEntry[] getPositionTable() {
    return positionTable;
  }

  public TryCatchTable getTryCatchTable() {
    return tryCatchTable;
  }

  public DebugLocalInfoTable<EV> getDebugLocalInfoTable() {
    return debugLocalInfoTable;
  }

  public DebugLocalInfo getDebugLocalInfo(EV valueIndex) {
    return debugLocalInfoTable == null ? null : debugLocalInfoTable.valueToLocalMap.get(valueIndex);
  }

  public int[] getDebugLocalEnds(int instructionValueIndex) {
    return debugLocalInfoTable == null ? null : debugLocalInfoTable.getEnds(instructionValueIndex);
  }

  @Override
  public LirIterator iterator() {
    return new LirIterator(new ByteArrayIterator(instructions));
  }

  @Override
  public String toString() {
    return new LirPrinter<>(this).prettyPrint();
  }
}
