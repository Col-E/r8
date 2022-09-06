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
import com.android.tools.r8.lightir.LIRBuilder.BlockIndexGetter;
import com.android.tools.r8.lightir.LIRBuilder.ValueIndexGetter;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.StringUtils.BraceType;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import java.util.Arrays;

public class LIRCode implements Iterable<LIRInstructionView> {

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
      this.tryCatchHandlers = tryCatchHandlers;
    }

    public CatchHandlers<Integer> getHandlersForBlock(int blockIndex) {
      return tryCatchHandlers.get(blockIndex);
    }
  }

  public static class DebugLocalInfoTable {
    private final Int2ReferenceMap<DebugLocalInfo> valueToLocalMap;
    private final Int2ReferenceMap<int[]> instructionToEndUseMap;

    public DebugLocalInfoTable(
        Int2ReferenceMap<DebugLocalInfo> valueToLocalMap,
        Int2ReferenceMap<int[]> instructionToEndUseMap) {
      assert !valueToLocalMap.isEmpty();
      assert !instructionToEndUseMap.isEmpty();
      this.valueToLocalMap = valueToLocalMap;
      this.instructionToEndUseMap = instructionToEndUseMap;
    }
  }

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

  /** Table of try-catch handlers for each basic block. */
  private final TryCatchTable tryCatchTable;

  /** Table of debug local information for each SSA value (if present). */
  private final DebugLocalInfoTable debugLocalInfoTable;

  public static <V, B> LIRBuilder<V, B> builder(
      DexMethod method,
      ValueIndexGetter<V> valueIndexGetter,
      BlockIndexGetter<B> blockIndexGetter,
      DexItemFactory factory) {
    return new LIRBuilder<V, B>(method, valueIndexGetter, blockIndexGetter, factory);
  }

  // Should be constructed using LIRBuilder.
  LIRCode(
      IRMetadata metadata,
      DexItem[] constants,
      PositionEntry[] positions,
      int argumentCount,
      byte[] instructions,
      int instructionCount,
      TryCatchTable tryCatchTable,
      DebugLocalInfoTable debugLocalInfoTable) {
    this.metadata = metadata;
    this.constants = constants;
    this.positionTable = positions;
    this.argumentCount = argumentCount;
    this.instructions = instructions;
    this.instructionCount = instructionCount;
    this.tryCatchTable = tryCatchTable;
    this.debugLocalInfoTable = debugLocalInfoTable;
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

  public DebugLocalInfoTable getDebugLocalInfoTable() {
    return debugLocalInfoTable;
  }

  public DebugLocalInfo getDebugLocalInfo(int valueIndex) {
    return debugLocalInfoTable == null ? null : debugLocalInfoTable.valueToLocalMap.get(valueIndex);
  }

  public int[] getDebugLocalEnds(int instructionIndex) {
    return debugLocalInfoTable == null
        ? null
        : debugLocalInfoTable.instructionToEndUseMap.get(instructionIndex);
  }

  @Override
  public LIRIterator iterator() {
    return new LIRIterator(new ByteArrayIterator(instructions));
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder("LIRCode{");
    builder
        .append("args:")
        .append(argumentCount)
        .append(", insn(num:")
        .append(instructionCount)
        .append(", size:")
        .append(instructions.length)
        .append("):{");
    int index = 0;
    for (LIRInstructionView view : this) {
      builder.append(index).append(':');
      builder.append(LIROpcodes.toString(view.getOpcode()));
      if (view.getRemainingOperandSizeInBytes() > 0) {
        builder.append("(size:").append(1 + view.getRemainingOperandSizeInBytes()).append(")");
      }
      if (++index < instructionCount) {
        builder.append(", ");
      }
    }
    builder.append("}, pool(size:").append(constants.length).append("):");
    StringUtils.append(builder, Arrays.asList(constants), ", ", BraceType.TUBORG);
    builder.append("}");
    return builder.toString();
  }
}
