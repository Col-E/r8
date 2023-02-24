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
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.CatchHandlers;
import com.android.tools.r8.ir.code.IRMetadata;
import com.android.tools.r8.ir.code.IfType;
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builder for constructing LIR code from IR. */
public class LirBuilder<V, EV> {

  private final DexItemFactory factory;
  private final ByteArrayWriter byteWriter = new ByteArrayWriter();
  private final LirWriter writer = new LirWriter(byteWriter);
  private final Reference2IntMap<DexItem> constants;
  private final List<PositionEntry> positionTable;
  private int argumentCount = 0;
  private int instructionCount = 0;
  private IRMetadata metadata = null;

  private final LirEncodingStrategy<V, EV> strategy;

  private Position currentPosition;
  private Position flushedPosition;

  private final Int2ReferenceMap<CatchHandlers<Integer>> tryCatchRanges =
      new Int2ReferenceOpenHashMap<>();

  // Mapping from SSA value definition to the local name index in the constant pool.
  private final Map<EV, DebugLocalInfo> debugLocals = new HashMap<>();
  // Mapping from instruction to the end usage of SSA values with debug local info.
  private final Int2ReferenceMap<int[]> debugLocalEnds = new Int2ReferenceOpenHashMap<>();

  // TODO(b/225838009): Reconsider this fixed space as the operand count for phis is much larger.
  // Pre-allocated space for caching value indexes when writing instructions.
  private static final int MAX_VALUE_COUNT = 10;
  private int[] valueIndexBuffer = new int[MAX_VALUE_COUNT];

  public LirBuilder(DexMethod method, LirEncodingStrategy<V, EV> strategy, DexItemFactory factory) {
    this.factory = factory;
    constants = new Reference2IntOpenHashMap<>();
    positionTable = new ArrayList<>();
    this.strategy = strategy;
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

  public LirBuilder<V, EV> setCurrentPosition(Position position) {
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

  private EV getEncodedValue(V value) {
    return strategy.getEncodedValue(value);
  }

  private int getEncodedValueIndex(EV value, int referencingInstructionIndex) {
    int referencingValueIndex = referencingInstructionIndex + argumentCount;
    return strategy.getEncodedValueIndexForReference(value, referencingValueIndex);
  }

  private int encodedValueIndexSize(int encodedValueIndex) {
    return ByteUtils.intEncodingSize(encodedValueIndex);
  }

  private void writeEncodedValueIndex(int encodedValueIndex) {
    ByteUtils.writeEncodedInt(encodedValueIndex, writer::writeOperand);
  }

  private int getBlockIndex(BasicBlock block) {
    return strategy.getBlockIndex(block);
  }

  private int blockIndexSize(int index) {
    return ByteUtils.intEncodingSize(index);
  }

  private void writeBlockIndex(int index) {
    ByteUtils.writeEncodedInt(index, writer::writeOperand);
  }

  public LirBuilder<V, EV> setMetadata(IRMetadata metadata) {
    this.metadata = metadata;
    return this;
  }

  public LirBuilder<V, EV> setDebugValue(DebugLocalInfo debugInfo, EV valueIndex) {
    DebugLocalInfo old = debugLocals.put(valueIndex, debugInfo);
    assert old == null;
    return this;
  }

  public LirBuilder<V, EV> setDebugLocalEnds(int instructionValueIndex, Set<V> endValues) {
    int size = endValues.size();
    int[] indices = new int[size];
    Iterator<V> iterator = endValues.iterator();
    for (int i = 0; i < size; i++) {
      EV value = getEncodedValue(iterator.next());
      // The index is already the value index (it has been offset by argument count).
      indices[i] = strategy.getEncodedValueIndexForReference(value, instructionValueIndex);
    }
    debugLocalEnds.put(instructionValueIndex, indices);
    return this;
  }

  public LirBuilder<V, EV> addArgument(int index, boolean knownToBeBoolean) {
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

  private LirBuilder<V, EV> addNoOperandInstruction(int opcode) {
    advanceInstructionState();
    writer.writeOneByteInstruction(opcode);
    return this;
  }

  private LirBuilder<V, EV> addOneItemInstruction(int opcode, DexItem item) {
    return addInstructionTemplate(opcode, Collections.singletonList(item), Collections.emptyList());
  }

  private LirBuilder<V, EV> addOneValueInstruction(int opcode, V value) {
    return addInstructionTemplate(
        opcode, Collections.emptyList(), Collections.singletonList(value));
  }

  private LirBuilder<V, EV> addInstructionTemplate(
      int opcode, List<DexItem> items, List<V> values) {
    assert values.size() < MAX_VALUE_COUNT;
    int instructionIndex = advanceInstructionState();
    int operandSize = 0;
    for (DexItem item : items) {
      operandSize += constantIndexSize(item);
    }
    for (int i = 0; i < values.size(); i++) {
      EV value = getEncodedValue(values.get(i));
      int encodedValueIndex = getEncodedValueIndex(value, instructionIndex);
      operandSize += encodedValueIndexSize(encodedValueIndex);
      valueIndexBuffer[i] = encodedValueIndex;
    }
    writer.writeInstruction(opcode, operandSize);
    for (DexItem item : items) {
      writeConstantIndex(item);
    }
    for (int i = 0; i < values.size(); i++) {
      writeEncodedValueIndex(valueIndexBuffer[i]);
    }
    return this;
  }

  public LirBuilder<V, EV> addConstNull() {
    return addNoOperandInstruction(LirOpcodes.ACONST_NULL);
  }

  public LirBuilder<V, EV> addConstInt(int value) {
    if (-1 <= value && value <= 5) {
      addNoOperandInstruction(LirOpcodes.ICONST_0 + value);
    } else {
      advanceInstructionState();
      writer.writeInstruction(LirOpcodes.ICONST, ByteUtils.intEncodingSize(value));
      ByteUtils.writeEncodedInt(value, writer::writeOperand);
    }
    return this;
  }

  public LirBuilder<V, EV> addConstNumber(ValueType type, long value) {
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

  public LirBuilder<V, EV> addConstString(DexString string) {
    return addOneItemInstruction(LirOpcodes.LDC, string);
  }

  public LirBuilder<V, EV> addDiv(NumericType type, V leftValue, V rightValue) {
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

  public LirBuilder<V, EV> addArrayLength(V array) {
    return addOneValueInstruction(LirOpcodes.ARRAYLENGTH, array);
  }

  public LirBuilder<V, EV> addStaticGet(DexField field) {
    return addOneItemInstruction(LirOpcodes.GETSTATIC, field);
  }

  public LirBuilder<V, EV> addInvokeInstruction(int opcode, DexMethod method, List<V> arguments) {
    return addInstructionTemplate(opcode, Collections.singletonList(method), arguments);
  }

  public LirBuilder<V, EV> addInvokeDirect(DexMethod method, List<V> arguments) {
    return addInvokeInstruction(LirOpcodes.INVOKEDIRECT, method, arguments);
  }

  public LirBuilder<V, EV> addInvokeVirtual(DexMethod method, List<V> arguments) {
    return addInvokeInstruction(LirOpcodes.INVOKEVIRTUAL, method, arguments);
  }

  public LirBuilder<V, EV> addReturn(V value) {
    throw new Unimplemented();
  }

  public LirBuilder<V, EV> addReturnVoid() {
    return addNoOperandInstruction(LirOpcodes.RETURN);
  }

  public LirBuilder<V, EV> addDebugPosition(Position position) {
    assert currentPosition == position;
    return addNoOperandInstruction(LirOpcodes.DEBUGPOS);
  }

  public void addFallthrough() {
    addNoOperandInstruction(LirOpcodes.FALLTHROUGH);
  }

  public LirBuilder<V, EV> addGoto(BasicBlock target) {
    int targetIndex = getBlockIndex(target);
    int operandSize = blockIndexSize(targetIndex);
    advanceInstructionState();
    writer.writeInstruction(LirOpcodes.GOTO, operandSize);
    writeBlockIndex(targetIndex);
    return this;
  }

  public LirBuilder<V, EV> addIf(
      IfType ifKind, ValueType valueType, V value, BasicBlock trueTarget) {
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
    int instructionIndex = advanceInstructionState();
    int targetIndex = getBlockIndex(trueTarget);
    int valueIndex = getEncodedValueIndex(getEncodedValue(value), instructionIndex);
    int operandSize = blockIndexSize(targetIndex) + encodedValueIndexSize(valueIndex);
    writer.writeInstruction(opcode, operandSize);
    writeBlockIndex(targetIndex);
    writeEncodedValueIndex(valueIndex);
    return this;
  }

  public LirBuilder<V, EV> addIfCmp(
      IfType ifKind, ValueType valueType, List<V> inValues, BasicBlock trueTarget) {
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
    int valueOneIndex = getEncodedValueIndex(getEncodedValue(inValues.get(0)), instructionIndex);
    int valueTwoIndex = getEncodedValueIndex(getEncodedValue(inValues.get(1)), instructionIndex);
    int operandSize =
        blockIndexSize(targetIndex)
            + encodedValueIndexSize(valueOneIndex)
            + encodedValueIndexSize(valueTwoIndex);
    writer.writeInstruction(opcode, operandSize);
    writeBlockIndex(targetIndex);
    writeEncodedValueIndex(valueOneIndex);
    writeEncodedValueIndex(valueTwoIndex);
    return this;
  }

  public LirBuilder<V, EV> addMoveException(DexType exceptionType) {
    return addOneItemInstruction(LirOpcodes.MOVEEXCEPTION, exceptionType);
  }

  public LirBuilder<V, EV> addPhi(TypeElement type, List<V> operands) {
    DexType dexType = toDexType(type);
    return addInstructionTemplate(LirOpcodes.PHI, Collections.singletonList(dexType), operands);
  }

  public LirBuilder<V, EV> addDebugLocalWrite(V src) {
    return addOneValueInstruction(LirOpcodes.DEBUGLOCALWRITE, src);
  }

  public LirCode<EV> build() {
    assert metadata != null;
    int constantsCount = constants.size();
    DexItem[] constantTable = new DexItem[constantsCount];
    constants.forEach((item, index) -> constantTable[index] = item);
    DebugLocalInfoTable<EV> debugTable =
        debugLocals.isEmpty() ? null : new DebugLocalInfoTable<>(debugLocals, debugLocalEnds);
    return new LirCode<>(
        metadata,
        constantTable,
        positionTable.toArray(new PositionEntry[positionTable.size()]),
        argumentCount,
        byteWriter.toByteArray(),
        instructionCount,
        new TryCatchTable(tryCatchRanges),
        debugTable,
        strategy.getSsaValueStrategy());
  }
}
