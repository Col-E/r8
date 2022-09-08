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
import com.android.tools.r8.lightir.LIRCode.DebugLocalInfoTable;
import com.android.tools.r8.lightir.LIRCode.PositionEntry;
import com.android.tools.r8.lightir.LIRCode.TryCatchTable;
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
public class LIRBuilder<V, B> {

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
  private final LIRWriter writer = new LIRWriter(byteWriter);
  private final Reference2IntMap<DexItem> constants;
  private final ValueIndexGetter<V> valueIndexGetter;
  private final BlockIndexGetter<B> blockIndexGetter;
  private final List<PositionEntry> positionTable;
  private int argumentCount = 0;
  private int instructionCount = 0;
  private IRMetadata metadata = null;

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

  public LIRBuilder(
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

  public LIRBuilder<V, B> setCurrentPosition(Position position) {
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

  private int valueIndexSize(int index) {
    return ByteUtils.intEncodingSize(index);
  }

  private void writeValueIndex(int index) {
    ByteUtils.writeEncodedInt(index, writer::writeOperand);
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

  public LIRBuilder<V, B> setMetadata(IRMetadata metadata) {
    this.metadata = metadata;
    return this;
  }

  public LIRBuilder<V, B> setDebugValue(DebugLocalInfo debugInfo, int valueIndex) {
    DebugLocalInfo old = debugLocals.put(valueIndex, debugInfo);
    assert old == null;
    return this;
  }

  public LIRBuilder<V, B> setDebugLocalEnds(int instructionIndex, Set<V> endValues) {
    int size = endValues.size();
    int[] indices = new int[size];
    Iterator<V> iterator = endValues.iterator();
    for (int i = 0; i < size; i++) {
      indices[i] = getValueIndex(iterator.next());
    }
    debugLocalEnds.put(instructionIndex, indices);
    return this;
  }

  public LIRBuilder<V, B> addArgument(int index, boolean knownToBeBoolean) {
    // Arguments are implicitly given by method descriptor and not an actual instruction.
    assert argumentCount == index;
    argumentCount++;
    return this;
  }

  private void advanceInstructionState() {
    if (!currentPosition.equals(flushedPosition)) {
      setPositionIndex(instructionCount, currentPosition);
      flushedPosition = currentPosition;
    }
    ++instructionCount;
  }

  private LIRBuilder<V, B> addNoOperandInstruction(int opcode) {
    advanceInstructionState();
    writer.writeOneByteInstruction(opcode);
    return this;
  }

  private LIRBuilder<V, B> addOneItemInstruction(int opcode, DexItem item) {
    return addInstructionTemplate(opcode, Collections.singletonList(item), Collections.emptyList());
  }

  private LIRBuilder<V, B> addOneValueInstruction(int opcode, V value) {
    return addInstructionTemplate(
        opcode, Collections.emptyList(), Collections.singletonList(value));
  }

  private LIRBuilder<V, B> addInstructionTemplate(int opcode, List<DexItem> items, List<V> values) {
    assert values.size() < MAX_VALUE_COUNT;
    advanceInstructionState();
    int operandSize = 0;
    for (DexItem item : items) {
      operandSize += constantIndexSize(item);
    }
    for (int i = 0; i < values.size(); i++) {
      V value = values.get(i);
      int valueIndex = getValueIndex(value);
      operandSize += valueIndexSize(valueIndex);
      valueIndexBuffer[i] = valueIndex;
    }
    writer.writeInstruction(opcode, operandSize);
    for (DexItem item : items) {
      writeConstantIndex(item);
    }
    for (int i = 0; i < values.size(); i++) {
      writeValueIndex(valueIndexBuffer[i]);
    }
    return this;
  }

  public LIRBuilder<V, B> addConstNull() {
    return addNoOperandInstruction(LIROpcodes.ACONST_NULL);
  }

  public LIRBuilder<V, B> addConstInt(int value) {
    if (0 <= value && value <= 5) {
      addNoOperandInstruction(LIROpcodes.ICONST_0 + value);
    } else {
      advanceInstructionState();
      writer.writeInstruction(LIROpcodes.ICONST, ByteUtils.intEncodingSize(value));
      ByteUtils.writeEncodedInt(value, writer::writeOperand);
    }
    return this;
  }

  public LIRBuilder<V, B> addConstNumber(ValueType type, long value) {
    switch (type) {
      case OBJECT:
        return addConstNull();
      case INT:
        {
          int intValue = (int) value;
          if (-1 <= intValue && intValue <= 5) {
            int opcode = LIROpcodes.ICONST_0 + intValue;
            return addNoOperandInstruction(opcode);
          }
          int opcode = LIROpcodes.ICONST;
          int size = ByteUtils.intEncodingSize(intValue);
          writer.writeInstruction(opcode, size);
          ByteUtils.writeEncodedInt(intValue, writer::writeOperand);
          return this;
        }
      case FLOAT:
      case LONG:
      case DOUBLE:
      default:
        throw new Unimplemented();
    }
  }

  public LIRBuilder<V, B> addConstString(DexString string) {
    return addOneItemInstruction(LIROpcodes.LDC, string);
  }

  public LIRBuilder<V, B> addDiv(NumericType type, V leftValue, V rightValue) {
    switch (type) {
      case BYTE:
      case CHAR:
      case SHORT:
      case INT:
        {
          return addInstructionTemplate(
              LIROpcodes.IDIV, Collections.emptyList(), ImmutableList.of(leftValue, rightValue));
        }
      case LONG:
      case FLOAT:
      case DOUBLE:
      default:
        throw new Unimplemented();
    }
  }

  public LIRBuilder<V, B> addArrayLength(V array) {
    return addOneValueInstruction(LIROpcodes.ARRAYLENGTH, array);
  }

  public LIRBuilder<V, B> addStaticGet(DexField field) {
    return addOneItemInstruction(LIROpcodes.GETSTATIC, field);
  }

  public LIRBuilder<V, B> addInvokeInstruction(int opcode, DexMethod method, List<V> arguments) {
    return addInstructionTemplate(opcode, Collections.singletonList(method), arguments);
  }

  public LIRBuilder<V, B> addInvokeDirect(DexMethod method, List<V> arguments) {
    return addInvokeInstruction(LIROpcodes.INVOKEDIRECT, method, arguments);
  }

  public LIRBuilder<V, B> addInvokeVirtual(DexMethod method, List<V> arguments) {
    return addInvokeInstruction(LIROpcodes.INVOKEVIRTUAL, method, arguments);
  }

  public LIRBuilder<V, B> addReturn(V value) {
    throw new Unimplemented();
  }

  public LIRBuilder<V, B> addReturnVoid() {
    return addNoOperandInstruction(LIROpcodes.RETURN);
  }

  public LIRBuilder<V, B> addDebugPosition(Position position) {
    assert currentPosition == position;
    return addNoOperandInstruction(LIROpcodes.DEBUGPOS);
  }

  public void addFallthrough() {
    addNoOperandInstruction(LIROpcodes.FALLTHROUGH);
  }

  public LIRBuilder<V, B> addGoto(B target) {
    int targetIndex = getBlockIndex(target);
    int operandSize = blockIndexSize(targetIndex);
    advanceInstructionState();
    writer.writeInstruction(LIROpcodes.GOTO, operandSize);
    writeBlockIndex(targetIndex);
    return this;
  }

  public LIRBuilder<V, B> addIf(Type ifKind, ValueType valueType, V value, B trueTarget) {
    int opcode;
    switch (ifKind) {
      case EQ:
        opcode = valueType.isObject() ? LIROpcodes.IFNULL : LIROpcodes.IFEQ;
        break;
      case GE:
        opcode = LIROpcodes.IFGE;
        break;
      case GT:
        opcode = LIROpcodes.IFGT;
        break;
      case LE:
        opcode = LIROpcodes.IFLE;
        break;
      case LT:
        opcode = LIROpcodes.IFLT;
        break;
      case NE:
        opcode = valueType.isObject() ? LIROpcodes.IFNONNULL : LIROpcodes.IFNE;
        break;
      default:
        throw new Unreachable("Unexpected if kind: " + ifKind);
    }
    int targetIndex = getBlockIndex(trueTarget);
    int valueIndex = getValueIndex(value);
    int operandSize = blockIndexSize(targetIndex) + valueIndexSize(valueIndex);
    advanceInstructionState();
    writer.writeInstruction(opcode, operandSize);
    writeBlockIndex(targetIndex);
    writeValueIndex(valueIndex);
    return this;
  }

  public LIRBuilder<V, B> addIfCmp(
      Type ifKind, ValueType valueType, List<V> inValues, B trueTarget) {
    int opcode;
    switch (ifKind) {
      case EQ:
        opcode = valueType.isObject() ? LIROpcodes.IF_ACMPEQ : LIROpcodes.IF_ICMPEQ;
        break;
      case GE:
        opcode = LIROpcodes.IF_ICMPGE;
        break;
      case GT:
        opcode = LIROpcodes.IF_ICMPGT;
        break;
      case LE:
        opcode = LIROpcodes.IF_ICMPLE;
        break;
      case LT:
        opcode = LIROpcodes.IF_ICMPLT;
        break;
      case NE:
        opcode = valueType.isObject() ? LIROpcodes.IF_ACMPNE : LIROpcodes.IF_ICMPNE;
        break;
      default:
        throw new Unreachable("Unexpected if kind " + ifKind);
    }
    int targetIndex = getBlockIndex(trueTarget);
    int valueOneIndex = getValueIndex(inValues.get(0));
    int valueTwoIndex = getValueIndex(inValues.get(1));
    int operandSize =
        blockIndexSize(targetIndex) + valueIndexSize(valueOneIndex) + valueIndexSize(valueTwoIndex);
    advanceInstructionState();
    writer.writeInstruction(opcode, operandSize);
    writeBlockIndex(targetIndex);
    writeValueIndex(valueOneIndex);
    writeValueIndex(valueTwoIndex);
    return this;
  }

  public LIRBuilder<V, B> addMoveException(DexType exceptionType) {
    return addOneItemInstruction(LIROpcodes.MOVEEXCEPTION, exceptionType);
  }

  public LIRBuilder<V, B> addPhi(TypeElement type, List<V> operands) {
    DexType dexType = toDexType(type);
    return addInstructionTemplate(LIROpcodes.PHI, Collections.singletonList(dexType), operands);
  }

  public LIRBuilder<V, B> addDebugLocalWrite(V src) {
    return addOneValueInstruction(LIROpcodes.DEBUGLOCALWRITE, src);
  }

  public LIRCode build() {
    assert metadata != null;
    int constantsCount = constants.size();
    DexItem[] constantTable = new DexItem[constantsCount];
    constants.forEach((item, index) -> constantTable[index] = item);
    DebugLocalInfoTable debugTable =
        debugLocals.isEmpty() ? null : new DebugLocalInfoTable(debugLocals, debugLocalEnds);
    return new LIRCode(
        metadata,
        constantTable,
        positionTable.toArray(new PositionEntry[positionTable.size()]),
        argumentCount,
        byteWriter.toByteArray(),
        instructionCount,
        new TryCatchTable(tryCatchRanges),
        debugTable);
  }
}
