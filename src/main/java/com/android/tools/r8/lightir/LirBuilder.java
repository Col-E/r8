// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.lightir;

import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItem;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.CatchHandlers;
import com.android.tools.r8.ir.code.IRMetadata;
import com.android.tools.r8.ir.code.If.Type;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Position.SyntheticPosition;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.lightir.LirCode.DebugLocalInfoTable;
import com.android.tools.r8.lightir.LirCode.PositionEntry;
import com.android.tools.r8.lightir.LirCode.TryCatchTable;
import com.android.tools.r8.utils.ListUtils;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Builder for constructing LIR code from IR.
 *
 * @param <V> Type of SSA values. This is abstract to ensure that value internals are not used in
 *     building.
 * @param <B> Type of basic blocks. This is abstract to ensure that basic block internals are not
 *     used in building.
 */
public class LirBuilder<V, B> {

  // Abstraction for the only accessible properties of an SSA value.
  public interface ValueIndexGetter<V> {
    int getValueIndex(V value);
  }

  // Abstraction for the only accessible properties of a basic block.
  public interface BlockIndexGetter<B> {
    int getBlockIndex(B block);
  }

  private final DexItemFactory factory;
  private final ByteArrayWriter byteWriter = new ByteArrayWriter();
  private final LirWriter writer = new LirWriter(byteWriter);
  private final Reference2IntMap<DexItem> constants;
  private final ValueIndexGetter<V> valueIndexGetter;
  private final BlockIndexGetter<B> blockIndexGetter;
  private final List<PositionEntry> positionTable;
  private int argumentCount = 0;
  private int instructionCount = 0;
  private IRMetadata metadata = null;
  private final LirSsaValueStrategy ssaValueStrategy = LirSsaValueStrategy.get();

  private Position currentPosition;
  private Position flushedPosition;

  private final Int2ReferenceMap<CatchHandlers<Integer>> tryCatchRanges =
      new Int2ReferenceOpenHashMap<>();

  // Mapping from SSA value definition to the local name index in the constant pool.
  private final Int2ReferenceMap<DebugLocalInfo> debugLocals = new Int2ReferenceOpenHashMap<>();
  // Mapping from instruction to the end usage of SSA values with debug local info.
  private final Int2ReferenceMap<int[]> debugLocalEnds = new Int2ReferenceOpenHashMap<>();

  // TODO(b/225838009): Reconsider this fixed space as the operand count for phis is much larger.
  // Pre-allocated space for caching value indexes when writing instructions.
  private static final int MAX_VALUE_COUNT = 10;
  private int[] valueIndexBuffer = new int[MAX_VALUE_COUNT];

  public LirBuilder(
      DexMethod method,
      ValueIndexGetter<V> valueIndexGetter,
      BlockIndexGetter<B> blockIndexGetter,
      DexItemFactory factory) {
    this.factory = factory;
    constants = new Reference2IntOpenHashMap<>();
    positionTable = new ArrayList<>();
    this.valueIndexGetter = valueIndexGetter;
    this.blockIndexGetter = blockIndexGetter;
    currentPosition = SyntheticPosition.builder().setLine(0).setMethod(method).build();
    flushedPosition = currentPosition;
  }

  public boolean verifyCurrentValueIndex(int valueIndex) {
    assert instructionCount + argumentCount == valueIndex;
    return true;
  }

  public DexType toDexType(TypeElement typeElement) {
    if (typeElement.isPrimitiveType()) {
      return typeElement.asPrimitiveType().toDexType(factory);
    }
    if (typeElement.isReferenceType()) {
      return typeElement.asReferenceType().toDexType(factory);
    }
    throw new Unreachable("Unexpected type element: " + typeElement);
  }

  public void addTryCatchHanders(int blockIndex, CatchHandlers<Integer> handlers) {
    tryCatchRanges.put(blockIndex, handlers);
  }

  public LirBuilder<V, B> setCurrentPosition(Position position) {
    assert position != null;
    assert position != Position.none();
    currentPosition = position;
    return this;
  }

  private void setPositionIndex(int instructionIndex, Position position) {
    assert positionTable.isEmpty()
        || ListUtils.last(positionTable).fromInstructionIndex < instructionIndex;
    positionTable.add(new PositionEntry(instructionIndex, position));
  }

  private int getConstantIndex(DexItem item) {
    int nextIndex = constants.size();
    Integer oldIndex = constants.putIfAbsent(item, nextIndex);
    return oldIndex != null ? oldIndex : nextIndex;
  }

  private int constantIndexSize(DexItem item) {
    return 4;
  }

  private void writeConstantIndex(DexItem item) {
    int index = getConstantIndex(item);
    assert constantIndexSize(item) == ByteUtils.intEncodingSize(index);
    ByteUtils.writeEncodedInt(index, writer::writeOperand);
  }

  private int getValueIndex(V value) {
    return valueIndexGetter.getValueIndex(value);
  }

  private int valueIndexSize(int valueIndex, int referencingInstructionIndex) {
    int referencingValueIndex = referencingInstructionIndex + argumentCount;
    int encodedValueIndex = ssaValueStrategy.encodeValueIndex(valueIndex, referencingValueIndex);
    return ByteUtils.intEncodingSize(encodedValueIndex);
  }

  private void writeValueIndex(int valueIndex, int referencingInstructionIndex) {
    int referencingValueIndex = referencingInstructionIndex + argumentCount;
    int encodedValueIndex = ssaValueStrategy.encodeValueIndex(valueIndex, referencingValueIndex);
    ByteUtils.writeEncodedInt(encodedValueIndex, writer::writeOperand);
  }

  private int getBlockIndex(B block) {
    return blockIndexGetter.getBlockIndex(block);
  }

  private int blockIndexSize(int index) {
    return ByteUtils.intEncodingSize(index);
  }

  private void writeBlockIndex(int index) {
    ByteUtils.writeEncodedInt(index, writer::writeOperand);
  }

  public LirBuilder<V, B> setMetadata(IRMetadata metadata) {
    this.metadata = metadata;
    return this;
  }

  public LirBuilder<V, B> setDebugValue(DebugLocalInfo debugInfo, int valueIndex) {
    DebugLocalInfo old = debugLocals.put(valueIndex, debugInfo);
    assert old == null;
    return this;
  }

  public LirBuilder<V, B> setDebugLocalEnds(int instructionValueIndex, Set<V> endValues) {
    int size = endValues.size();
    int[] indices = new int[size];
    Iterator<V> iterator = endValues.iterator();
    for (int i = 0; i < size; i++) {
      indices[i] = getValueIndex(iterator.next());
    }
    debugLocalEnds.put(instructionValueIndex, indices);
    return this;
  }

  public LirBuilder<V, B> addArgument(int index, boolean knownToBeBoolean) {
    // Arguments are implicitly given by method descriptor and not an actual instruction.
    assert argumentCount == index;
    argumentCount++;
    return this;
  }

  private int advanceInstructionState() {
    if (!currentPosition.equals(flushedPosition)) {
      setPositionIndex(instructionCount, currentPosition);
      flushedPosition = currentPosition;
    }
    return instructionCount++;
  }

  private LirBuilder<V, B> addNoOperandInstruction(int opcode) {
    advanceInstructionState();
    writer.writeOneByteInstruction(opcode);
    return this;
  }

  private LirBuilder<V, B> addOneItemInstruction(int opcode, DexItem item) {
    return addInstructionTemplate(opcode, Collections.singletonList(item), Collections.emptyList());
  }

  private LirBuilder<V, B> addOneValueInstruction(int opcode, V value) {
    return addInstructionTemplate(
        opcode, Collections.emptyList(), Collections.singletonList(value));
  }

  private LirBuilder<V, B> addInstructionTemplate(int opcode, List<DexItem> items, List<V> values) {
    assert values.size() < MAX_VALUE_COUNT;
    int instructionIndex = advanceInstructionState();
    int operandSize = 0;
    for (DexItem item : items) {
      operandSize += constantIndexSize(item);
    }
    for (int i = 0; i < values.size(); i++) {
      V value = values.get(i);
      int valueIndex = getValueIndex(value);
      operandSize += valueIndexSize(valueIndex, instructionIndex);
      valueIndexBuffer[i] = valueIndex;
    }
    writer.writeInstruction(opcode, operandSize);
    for (DexItem item : items) {
      writeConstantIndex(item);
    }
    for (int i = 0; i < values.size(); i++) {
      writeValueIndex(valueIndexBuffer[i], instructionIndex);
    }
    return this;
  }

  public LirBuilder<V, B> addConstNull() {
    return addNoOperandInstruction(LirOpcodes.ACONST_NULL);
  }

  public LirBuilder<V, B> addConstInt(int value) {
    if (-1 <= value && value <= 5) {
      addNoOperandInstruction(LirOpcodes.ICONST_0 + value);
    } else {
      advanceInstructionState();
      writer.writeInstruction(LirOpcodes.ICONST, ByteUtils.intEncodingSize(value));
      ByteUtils.writeEncodedInt(value, writer::writeOperand);
    }
    return this;
  }

  public LirBuilder<V, B> addConstNumber(ValueType type, long value) {
    switch (type) {
      case OBJECT:
        return addConstNull();
      case INT:
        return addConstInt((int) value);
      case FLOAT:
      case LONG:
      case DOUBLE:
      default:
        throw new Unimplemented();
    }
  }

  public LirBuilder<V, B> addConstString(DexString string) {
    return addOneItemInstruction(LirOpcodes.LDC, string);
  }

  public LirBuilder<V, B> addDiv(NumericType type, V leftValue, V rightValue) {
    switch (type) {
      case BYTE:
      case CHAR:
      case SHORT:
      case INT:
        {
          return addInstructionTemplate(
              LirOpcodes.IDIV, Collections.emptyList(), ImmutableList.of(leftValue, rightValue));
        }
      case LONG:
      case FLOAT:
      case DOUBLE:
      default:
        throw new Unimplemented();
    }
  }

  public LirBuilder<V, B> addArrayLength(V array) {
    return addOneValueInstruction(LirOpcodes.ARRAYLENGTH, array);
  }

  public LirBuilder<V, B> addStaticGet(DexField field) {
    return addOneItemInstruction(LirOpcodes.GETSTATIC, field);
  }

  public LirBuilder<V, B> addInvokeInstruction(int opcode, DexMethod method, List<V> arguments) {
    return addInstructionTemplate(opcode, Collections.singletonList(method), arguments);
  }

  public LirBuilder<V, B> addInvokeDirect(DexMethod method, List<V> arguments) {
    return addInvokeInstruction(LirOpcodes.INVOKEDIRECT, method, arguments);
  }

  public LirBuilder<V, B> addInvokeVirtual(DexMethod method, List<V> arguments) {
    return addInvokeInstruction(LirOpcodes.INVOKEVIRTUAL, method, arguments);
  }

  public LirBuilder<V, B> addReturn(V value) {
    throw new Unimplemented();
  }

  public LirBuilder<V, B> addReturnVoid() {
    return addNoOperandInstruction(LirOpcodes.RETURN);
  }

  public LirBuilder<V, B> addDebugPosition(Position position) {
    assert currentPosition == position;
    return addNoOperandInstruction(LirOpcodes.DEBUGPOS);
  }

  public void addFallthrough() {
    addNoOperandInstruction(LirOpcodes.FALLTHROUGH);
  }

  public LirBuilder<V, B> addGoto(B target) {
    int targetIndex = getBlockIndex(target);
    int operandSize = blockIndexSize(targetIndex);
    advanceInstructionState();
    writer.writeInstruction(LirOpcodes.GOTO, operandSize);
    writeBlockIndex(targetIndex);
    return this;
  }

  public LirBuilder<V, B> addIf(Type ifKind, ValueType valueType, V value, B trueTarget) {
    int opcode;
    switch (ifKind) {
      case EQ:
        opcode = valueType.isObject() ? LirOpcodes.IFNULL : LirOpcodes.IFEQ;
        break;
      case GE:
        opcode = LirOpcodes.IFGE;
        break;
      case GT:
        opcode = LirOpcodes.IFGT;
        break;
      case LE:
        opcode = LirOpcodes.IFLE;
        break;
      case LT:
        opcode = LirOpcodes.IFLT;
        break;
      case NE:
        opcode = valueType.isObject() ? LirOpcodes.IFNONNULL : LirOpcodes.IFNE;
        break;
      default:
        throw new Unreachable("Unexpected if kind: " + ifKind);
    }
    int targetIndex = getBlockIndex(trueTarget);
    int valueIndex = getValueIndex(value);
    int operandSize = blockIndexSize(targetIndex) + valueIndexSize(valueIndex, instructionCount);
    int instructionIndex = advanceInstructionState();
    writer.writeInstruction(opcode, operandSize);
    writeBlockIndex(targetIndex);
    writeValueIndex(valueIndex, instructionIndex);
    return this;
  }

  public LirBuilder<V, B> addIfCmp(
      Type ifKind, ValueType valueType, List<V> inValues, B trueTarget) {
    int opcode;
    switch (ifKind) {
      case EQ:
        opcode = valueType.isObject() ? LirOpcodes.IF_ACMPEQ : LirOpcodes.IF_ICMPEQ;
        break;
      case GE:
        opcode = LirOpcodes.IF_ICMPGE;
        break;
      case GT:
        opcode = LirOpcodes.IF_ICMPGT;
        break;
      case LE:
        opcode = LirOpcodes.IF_ICMPLE;
        break;
      case LT:
        opcode = LirOpcodes.IF_ICMPLT;
        break;
      case NE:
        opcode = valueType.isObject() ? LirOpcodes.IF_ACMPNE : LirOpcodes.IF_ICMPNE;
        break;
      default:
        throw new Unreachable("Unexpected if kind " + ifKind);
    }
    int instructionIndex = advanceInstructionState();
    int targetIndex = getBlockIndex(trueTarget);
    int valueOneIndex = getValueIndex(inValues.get(0));
    int valueTwoIndex = getValueIndex(inValues.get(1));
    int operandSize =
        blockIndexSize(targetIndex)
            + valueIndexSize(valueOneIndex, instructionIndex)
            + valueIndexSize(valueTwoIndex, instructionIndex);
    writer.writeInstruction(opcode, operandSize);
    writeBlockIndex(targetIndex);
    writeValueIndex(valueOneIndex, instructionIndex);
    writeValueIndex(valueTwoIndex, instructionIndex);
    return this;
  }

  public LirBuilder<V, B> addMoveException(DexType exceptionType) {
    return addOneItemInstruction(LirOpcodes.MOVEEXCEPTION, exceptionType);
  }

  public LirBuilder<V, B> addPhi(TypeElement type, List<V> operands) {
    DexType dexType = toDexType(type);
    return addInstructionTemplate(LirOpcodes.PHI, Collections.singletonList(dexType), operands);
  }

  public LirBuilder<V, B> addDebugLocalWrite(V src) {
    return addOneValueInstruction(LirOpcodes.DEBUGLOCALWRITE, src);
  }

  public LirCode build() {
    assert metadata != null;
    int constantsCount = constants.size();
    DexItem[] constantTable = new DexItem[constantsCount];
    constants.forEach((item, index) -> constantTable[index] = item);
    DebugLocalInfoTable debugTable =
        debugLocals.isEmpty() ? null : new DebugLocalInfoTable(debugLocals, debugLocalEnds);
    return new LirCode(
        metadata,
        constantTable,
        positionTable.toArray(new PositionEntry[positionTable.size()]),
        argumentCount,
        byteWriter.toByteArray(),
        instructionCount,
        new TryCatchTable(tryCatchRanges),
        debugTable,
        ssaValueStrategy);
  }
}
