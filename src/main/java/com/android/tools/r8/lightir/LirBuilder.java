// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.lightir;

import com.android.tools.r8.cf.code.CfArithmeticBinop;
import com.android.tools.r8.cf.code.CfArithmeticBinop.Opcode;
import com.android.tools.r8.cf.code.CfLogicalBinop;
import com.android.tools.r8.cf.code.CfNumberConversion;
import com.android.tools.r8.dex.MixedSectionCollection;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItem;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeInstructionMetadata;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.CatchHandlers;
import com.android.tools.r8.ir.code.Cmp;
import com.android.tools.r8.ir.code.Cmp.Bias;
import com.android.tools.r8.ir.code.IRMetadata;
import com.android.tools.r8.ir.code.IfType;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.MonitorType;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Position.SyntheticPosition;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.lightir.LirCode.DebugLocalInfoTable;
import com.android.tools.r8.lightir.LirCode.LinePositionEntry;
import com.android.tools.r8.lightir.LirCode.PositionEntry;
import com.android.tools.r8.lightir.LirCode.StructuredPositionEntry;
import com.android.tools.r8.lightir.LirCode.TryCatchTable;
import com.android.tools.r8.naming.dexitembasedstring.NameComputationInfo;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ListUtils;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceSortedMap;
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
  private static final int FLOAT_0 = Float.floatToRawIntBits(0);
  private static final int FLOAT_1 = Float.floatToRawIntBits(1);
  private static final int FLOAT_2 = Float.floatToRawIntBits(2);
  private static final long DOUBLE_0 = Double.doubleToRawLongBits(0);
  private static final long DOUBLE_1 = Double.doubleToRawLongBits(1);

  private final boolean useDexEstimationStrategy;
  private final DexItemFactory factory;
  private final ByteArrayWriter byteWriter = new ByteArrayWriter();
  private final LirWriter writer = new LirWriter(byteWriter);
  private final Reference2IntMap<DexItem> constants;
  private final List<PositionEntry> positionTable;
  private int argumentCount = 0;
  private int instructionCount = 0;
  private IRMetadata metadata = null;

  private final LirEncodingStrategy<V, EV> strategy;

  private BytecodeInstructionMetadata currentMetadata;
  private Int2ReferenceMap<BytecodeInstructionMetadata> metadataMap;

  private Position currentPosition;
  private Position flushedPosition;

  private final Int2ReferenceMap<CatchHandlers<Integer>> tryCatchRanges =
      new Int2ReferenceOpenHashMap<>();

  // Mapping from SSA value definition to the local name index in the constant pool.
  private final Map<EV, DebugLocalInfo> debugLocals = new HashMap<>();
  // Mapping from instruction to the end usage of SSA values with debug local info.
  private final Int2ReferenceMap<int[]> debugLocalEnds = new Int2ReferenceOpenHashMap<>();

  /**
   * Internal "DexItem" for the instruction payloads such that they can be put in the pool.
   *
   * <p>The instruction encoding assumes the instruction operand payload size is u1, so this allows
   * the data payload to be stored in the constant pool instead.
   */
  public abstract static class InstructionPayload extends DexItem {
    @Override
    protected final void collectMixedSectionItems(MixedSectionCollection collection) {
      throw new Unreachable();
    }
  }

  /**
   * Internal "DexItem" for the fill-array payloads such that they can be put in the pool.
   *
   * <p>The instruction encoding assumes the instruction operand payload size is u1, so the data
   * payload is stored in the constant pool instead.
   */
  public static class FillArrayPayload extends InstructionPayload {
    public final int element_width;
    public final long size;
    public final short[] data;

    public FillArrayPayload(int element_width, long size, short[] data) {
      this.element_width = element_width;
      this.size = size;
      this.data = data;
    }
  }

  public static class IntSwitchPayload extends InstructionPayload {
    public final int[] keys;
    public final int[] targets;

    public IntSwitchPayload(int[] keys, int[] targets) {
      assert keys.length == targets.length;
      this.keys = keys;
      this.targets = targets;
    }
  }

  public static class StringSwitchPayload extends InstructionPayload {
    public final int[] keys;
    public final int[] targets;

    public StringSwitchPayload(int[] keys, int[] targets) {
      assert keys.length == targets.length;
      this.keys = keys;
      this.targets = targets;
    }
  }

  public static class NameComputationPayload extends InstructionPayload {
    public final NameComputationInfo<?> nameComputationInfo;

    public NameComputationPayload(NameComputationInfo<?> nameComputationInfo) {
      this.nameComputationInfo = nameComputationInfo;
    }
  }

  public static class RecordFieldValuesPayload extends InstructionPayload {
    public final DexField[] fields;

    public RecordFieldValuesPayload(DexField[] fields) {
      this.fields = fields;
    }
  }

  public LirBuilder(
      DexMethod method, LirEncodingStrategy<V, EV> strategy, InternalOptions options) {
    useDexEstimationStrategy = options.isGeneratingDex();
    factory = options.dexItemFactory();
    constants = new Reference2IntOpenHashMap<>();
    positionTable = new ArrayList<>();
    this.strategy = strategy;
    currentPosition = SyntheticPosition.builder().setLine(0).setMethod(method).build();
    flushedPosition = currentPosition;
  }

  public DexItemFactory factory() {
    return factory;
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

  public LirBuilder<V, EV> prepareForBytecodeInstructionMetadata(int expectedSize) {
    if (expectedSize > 0) {
      metadataMap = new Int2ReferenceOpenHashMap<>(expectedSize);
    }
    return this;
  }

  public LirBuilder<V, EV> setCurrentMetadata(BytecodeInstructionMetadata metadata) {
    currentMetadata = metadata;
    return this;
  }

  public LirBuilder<V, EV> setCurrentPosition(Position position) {
    assert position != null;
    if (!position.isNone()) {
      currentPosition = position;
    }
    return this;
  }

  private boolean isSimpleLinePosition(Position position) {
    return (position.isSourcePosition() || position.isSyntheticPosition())
        && !position.hasCallerPosition();
  }

  private boolean setPositionIndex(int instructionIndex, Position position) {
    assert positionTable.isEmpty()
        || ListUtils.last(positionTable).getFromInstructionIndex() < instructionIndex;

    if (!isSimpleLinePosition(position)) {
      positionTable.add(new StructuredPositionEntry(instructionIndex, position));
      return true;
    }

    // Don't emit simple preamble lines in the table.
    if (positionTable.isEmpty() && position.getLine() == 0) {
      return false;
    }

    // Due to source/synthetic lines we may have non-equal positions with the same simple lines.
    if (!positionTable.isEmpty()) {
      PositionEntry last = ListUtils.last(positionTable);
      if (last instanceof LinePositionEntry) {
        if (((LinePositionEntry) last).getLine() == position.getLine()) {
          return false;
        }
      }
    }

    positionTable.add(new LinePositionEntry(instructionIndex, position.getLine()));
    return true;
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
      if (setPositionIndex(instructionCount, currentPosition)) {
        flushedPosition = currentPosition;
      }
    }
    if (currentMetadata != null) {
      metadataMap.put(instructionCount, currentMetadata);
      currentMetadata = null;
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

  private LirBuilder<V, EV> addTwoValueInstruction(int opcode, V leftValue, V rightValue) {
    return addInstructionTemplate(
        opcode, Collections.emptyList(), ImmutableList.of(leftValue, rightValue));
  }

  private LirBuilder<V, EV> addInstructionTemplate(
      int opcode, List<DexItem> items, List<V> values) {
    int instructionIndex = advanceInstructionState();
    int operandSize = 0;
    for (DexItem item : items) {
      operandSize += constantIndexSize(item);
    }
    for (int i = 0; i < values.size(); i++) {
      EV value = getEncodedValue(values.get(i));
      int encodedValueIndex = getEncodedValueIndex(value, instructionIndex);
      operandSize += encodedValueIndexSize(encodedValueIndex);
    }
    writer.writeInstruction(opcode, operandSize);
    for (DexItem item : items) {
      writeConstantIndex(item);
    }
    for (int i = 0; i < values.size(); i++) {
      // TODO(b/225838009): Consider backpatching operand size to avoid recomputing value indexes.
      EV value = getEncodedValue(values.get(i));
      int encodedValueIndex = getEncodedValueIndex(value, instructionIndex);
      writeEncodedValueIndex(encodedValueIndex);
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

  public LirBuilder<V, EV> addConstFloat(int value) {
    if (value == FLOAT_0) {
      return addNoOperandInstruction(LirOpcodes.FCONST_0);
    }
    if (value == FLOAT_1) {
      return addNoOperandInstruction(LirOpcodes.FCONST_1);
    }
    if (value == FLOAT_2) {
      return addNoOperandInstruction(LirOpcodes.FCONST_2);
    }
    advanceInstructionState();
    writer.writeInstruction(LirOpcodes.FCONST, ByteUtils.intEncodingSize(value));
    ByteUtils.writeEncodedInt(value, writer::writeOperand);
    return this;
  }

  public LirBuilder<V, EV> addConstLong(long value) {
    if (value == 0) {
      return addNoOperandInstruction(LirOpcodes.LCONST_0);
    }
    if (value == 1) {
      return addNoOperandInstruction(LirOpcodes.LCONST_1);
    }
    advanceInstructionState();
    writer.writeInstruction(LirOpcodes.LCONST, ByteUtils.longEncodingSize(value));
    ByteUtils.writeEncodedLong(value, writer::writeOperand);
    return this;
  }

  public LirBuilder<V, EV> addConstDouble(long value) {
    if (value == DOUBLE_0) {
      return addNoOperandInstruction(LirOpcodes.DCONST_0);
    }
    if (value == DOUBLE_1) {
      return addNoOperandInstruction(LirOpcodes.DCONST_1);
    }
    advanceInstructionState();
    writer.writeInstruction(LirOpcodes.DCONST, ByteUtils.longEncodingSize(value));
    ByteUtils.writeEncodedLong(value, writer::writeOperand);
    return this;
  }

  public LirBuilder<V, EV> addConstNumber(ValueType type, long value) {
    switch (type) {
      case OBJECT:
        return addConstNull();
      case INT:
        return addConstInt((int) value);
      case FLOAT:
        return addConstFloat((int) value);
      case LONG:
        return addConstLong(value);
      case DOUBLE:
        return addConstDouble(value);
      default:
        throw new Unreachable();
    }
  }

  public LirBuilder<V, EV> addConstString(DexString string) {
    return addOneItemInstruction(LirOpcodes.LDC, string);
  }

  public LirBuilder<V, EV> addConstClass(DexType type, boolean ignoreCompatRules) {
    int opcode = ignoreCompatRules ? LirOpcodes.CONSTCLASS_IGNORE_COMPAT : LirOpcodes.LDC;
    return addOneItemInstruction(opcode, type);
  }

  public LirBuilder<V, EV> addConstMethodHandle(DexMethodHandle methodHandle) {
    return addOneItemInstruction(LirOpcodes.LDC, methodHandle);
  }

  public LirBuilder<V, EV> addConstMethodType(DexProto methodType) {
    return addOneItemInstruction(LirOpcodes.LDC, methodType);
  }

  public LirBuilder<V, EV> addNeg(NumericType type, V value) {
    int opcode;
    switch (type) {
      case BYTE:
      case CHAR:
      case SHORT:
      case INT:
        opcode = LirOpcodes.INEG;
        break;
      case LONG:
        opcode = LirOpcodes.LNEG;
        break;
      case FLOAT:
        opcode = LirOpcodes.FNEG;
        break;
      case DOUBLE:
        opcode = LirOpcodes.DNEG;
        break;
      default:
        throw new Unreachable("Unexpected type: " + type);
    }
    return addOneValueInstruction(opcode, value);
  }

  public LirBuilder<V, EV> addNot(NumericType type, V value) {
    int opcode;
    switch (type) {
      case BYTE:
      case CHAR:
      case SHORT:
      case INT:
        opcode = LirOpcodes.INOT;
        break;
      case LONG:
        opcode = LirOpcodes.LNOT;
        break;
      default:
        throw new Unreachable("Unexpected type: " + type);
    }
    return addOneValueInstruction(opcode, value);
  }

  public LirBuilder<V, EV> addDiv(NumericType type, V leftValue, V rightValue) {
    int opcode;
    switch (type) {
      case BYTE:
      case CHAR:
      case SHORT:
      case INT:
        {
          opcode = LirOpcodes.IDIV;
          break;
        }
      case LONG:
        {
          opcode = LirOpcodes.LDIV;
          break;
        }
      case FLOAT:
        {
          opcode = LirOpcodes.FDIV;
          break;
        }
      case DOUBLE:
        {
          opcode = LirOpcodes.DDIV;
          break;
        }
      default:
        throw new Unreachable("Unexpected type: " + type);
    }
    return addInstructionTemplate(
        opcode, Collections.emptyList(), ImmutableList.of(leftValue, rightValue));
  }

  public LirBuilder<V, EV> addArrayLength(V array) {
    return addOneValueInstruction(LirOpcodes.ARRAYLENGTH, array);
  }

  public LirBuilder<V, EV> addCheckCast(DexType type, V value, boolean ignoreCompatRules) {
    int opcode = ignoreCompatRules ? LirOpcodes.CHECKCAST_IGNORE_COMPAT : LirOpcodes.CHECKCAST;
    return addInstructionTemplate(
        opcode, Collections.singletonList(type), Collections.singletonList(value));
  }

  public LirBuilder<V, EV> addSafeCheckCast(DexType type, V value) {
    return addInstructionTemplate(
        LirOpcodes.CHECKCAST_SAFE,
        Collections.singletonList(type),
        Collections.singletonList(value));
  }

  public LirBuilder<V, EV> addInstanceOf(DexType type, V value) {
    return addInstructionTemplate(
        LirOpcodes.INSTANCEOF, Collections.singletonList(type), Collections.singletonList(value));
  }

  public LirBuilder<V, EV> addStaticGet(DexField field) {
    return addOneItemInstruction(LirOpcodes.GETSTATIC, field);
  }

  public LirBuilder<V, EV> addStaticPut(DexField field, V value) {
    return addInstructionTemplate(
        LirOpcodes.PUTSTATIC, Collections.singletonList(field), ImmutableList.of(value));
  }

  public LirBuilder<V, EV> addInstanceGet(DexField field, V object) {
    return addInstructionTemplate(
        LirOpcodes.GETFIELD, Collections.singletonList(field), Collections.singletonList(object));
  }

  public LirBuilder<V, EV> addInstancePut(DexField field, V object, V value) {
    return addInstructionTemplate(
        LirOpcodes.PUTFIELD, Collections.singletonList(field), ImmutableList.of(object, value));
  }

  public LirBuilder<V, EV> addInvokeInstruction(int opcode, DexItem method, List<V> arguments) {
    return addInstructionTemplate(opcode, Collections.singletonList(method), arguments);
  }

  public LirBuilder<V, EV> addInvokeDirect(
      DexMethod method, List<V> arguments, boolean isInterface) {
    int opcode = isInterface ? LirOpcodes.INVOKEDIRECT_ITF : LirOpcodes.INVOKEDIRECT;
    return addInvokeInstruction(opcode, method, arguments);
  }

  public LirBuilder<V, EV> addInvokeSuper(
      DexMethod method, List<V> arguments, boolean isInterface) {
    int opcode = isInterface ? LirOpcodes.INVOKESUPER_ITF : LirOpcodes.INVOKESUPER;
    return addInvokeInstruction(opcode, method, arguments);
  }

  public LirBuilder<V, EV> addInvokeVirtual(DexMethod method, List<V> arguments) {
    return addInvokeInstruction(LirOpcodes.INVOKEVIRTUAL, method, arguments);
  }

  public LirBuilder<V, EV> addInvokeStatic(
      DexMethod method, List<V> arguments, boolean isInterface) {
    int opcode = isInterface ? LirOpcodes.INVOKESTATIC_ITF : LirOpcodes.INVOKESTATIC;
    return addInvokeInstruction(opcode, method, arguments);
  }

  public LirBuilder<V, EV> addInvokeInterface(DexMethod method, List<V> arguments) {
    return addInvokeInstruction(LirOpcodes.INVOKEINTERFACE, method, arguments);
  }

  public LirBuilder<V, EV> addInvokeCustom(DexCallSite callSite, List<V> arguments) {
    return addInvokeInstruction(LirOpcodes.INVOKEDYNAMIC, callSite, arguments);
  }

  public LirBuilder<V, EV> addInvokePolymorphic(
      DexMethod invokedMethod, DexProto proto, List<V> arguments) {
    return addInstructionTemplate(
        LirOpcodes.INVOKEPOLYMORPHIC, ImmutableList.of(invokedMethod, proto), arguments);
  }

  public LirBuilder<V, EV> addNewInstance(DexType clazz) {
    return addOneItemInstruction(LirOpcodes.NEW, clazz);
  }

  public LirBuilder<V, EV> addThrow(V exception) {
    return addOneValueInstruction(LirOpcodes.ATHROW, exception);
  }

  public LirBuilder<V, EV> addReturn(V value) {
    return addOneValueInstruction(LirOpcodes.ARETURN, value);
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

  public LirBuilder<V, EV> addIntSwitch(
      V value,
      int[] keys,
      Int2ReferenceSortedMap<BasicBlock> keyToTargetMap,
      BasicBlock fallthroughBlock) {
    int[] targets = new int[keys.length];
    for (int i = 0; i < keys.length; i++) {
      targets[i] = getBlockIndex(keyToTargetMap.get(keys[i]));
    }
    IntSwitchPayload payload = new IntSwitchPayload(keys, targets);
    return addInstructionTemplate(
        LirOpcodes.TABLESWITCH,
        Collections.singletonList(payload),
        Collections.singletonList(value));
  }

  public LirBuilder<V, EV> addStringSwitch(
      V value, DexString[] keys, BasicBlock[] targetsBlocks, BasicBlock fallthroughBlock) {
    int size = keys.length;
    assert targetsBlocks.length == size;
    int[] keyIndices = new int[size];
    int[] targetsIndices = new int[size];
    for (int i = 0; i < size; i++) {
      keyIndices[i] = getConstantIndex(keys[i]);
      targetsIndices[i] = getBlockIndex(targetsBlocks[i]);
    }
    StringSwitchPayload payload = new StringSwitchPayload(keyIndices, targetsIndices);
    return addInstructionTemplate(
        LirOpcodes.STRINGSWITCH,
        Collections.singletonList(payload),
        Collections.singletonList(value));
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

  public LirBuilder<V, EV> addDebugLocalRead() {
    return addNoOperandInstruction(LirOpcodes.DEBUGLOCALREAD);
  }

  public LirCode<EV> build() {
    assert metadata != null;
    int constantsCount = constants.size();
    DexItem[] constantTable = new DexItem[constantsCount];
    constants.forEach((item, index) -> constantTable[index] = item);
    DebugLocalInfoTable<EV> debugTable =
        debugLocals.isEmpty() ? null : new DebugLocalInfoTable<>(debugLocals, debugLocalEnds);
    TryCatchTable tryCatchTable =
        tryCatchRanges.isEmpty() ? null : new TryCatchTable(tryCatchRanges);
    return new LirCode<>(
        metadata,
        constantTable,
        positionTable.toArray(new PositionEntry[positionTable.size()]),
        argumentCount,
        byteWriter.toByteArray(),
        instructionCount,
        tryCatchTable,
        debugTable,
        strategy.getStrategyInfo(),
        useDexEstimationStrategy,
        metadataMap);
  }

  private int getCmpOpcode(NumericType type, Cmp.Bias bias) {
    switch (type) {
      case LONG:
        return LirOpcodes.LCMP;
      case FLOAT:
        return bias == Cmp.Bias.LT ? LirOpcodes.FCMPL : LirOpcodes.FCMPG;
      case DOUBLE:
        return bias == Cmp.Bias.LT ? LirOpcodes.DCMPL : LirOpcodes.DCMPG;
      default:
        throw new Unreachable("Cmp has unknown type " + type);
    }
  }

  public LirBuilder<V, EV> addCmp(NumericType type, Bias bias, V leftValue, V rightValue) {
    return addTwoValueInstruction(getCmpOpcode(type, bias), leftValue, rightValue);
  }

  public LirBuilder<V, EV> addArithmeticBinop(
      CfArithmeticBinop.Opcode binop, NumericType type, V leftValue, V rightValue) {
    // The LIR and CF opcodes are the same values, check that the two endpoints match.
    assert LirOpcodes.IADD == CfArithmeticBinop.getAsmOpcode(Opcode.Add, NumericType.INT);
    assert LirOpcodes.DREM == CfArithmeticBinop.getAsmOpcode(Opcode.Rem, NumericType.DOUBLE);
    int opcode = CfArithmeticBinop.getAsmOpcode(binop, type);
    return addTwoValueInstruction(opcode, leftValue, rightValue);
  }

  public LirBuilder<V, EV> addLogicalBinop(
      CfLogicalBinop.Opcode binop, NumericType type, V leftValue, V rightValue) {
    // The LIR and CF opcodes are the same values, check that the two endpoints match.
    assert LirOpcodes.ISHL
        == CfLogicalBinop.getAsmOpcode(CfLogicalBinop.Opcode.Shl, NumericType.INT);
    assert LirOpcodes.LXOR
        == CfLogicalBinop.getAsmOpcode(CfLogicalBinop.Opcode.Xor, NumericType.LONG);
    int opcode = CfLogicalBinop.getAsmOpcode(binop, type);
    return addTwoValueInstruction(opcode, leftValue, rightValue);
  }

  public LirBuilder<V, EV> addMonitor(MonitorType type, V value) {
    return addOneValueInstruction(
        type == MonitorType.ENTER ? LirOpcodes.MONITORENTER : LirOpcodes.MONITOREXIT, value);
  }

  public LirBuilder<V, EV> addNewArrayEmpty(V size, DexType type) {
    return addInstructionTemplate(
        LirOpcodes.NEWARRAY, Collections.singletonList(type), Collections.singletonList(size));
  }

  public LirBuilder<V, EV> addInvokeMultiNewArray(DexType type, List<V> arguments) {
    return addInstructionTemplate(
        LirOpcodes.MULTIANEWARRAY, Collections.singletonList(type), arguments);
  }

  public LirBuilder<V, EV> addInvokeNewArray(DexType type, List<V> arguments) {
    return addInstructionTemplate(
        LirOpcodes.INVOKENEWARRAY, Collections.singletonList(type), arguments);
  }

  public LirBuilder<V, EV> addNewArrayFilledData(int elementWidth, long size, short[] data, V src) {
    FillArrayPayload payloadConstant = new FillArrayPayload(elementWidth, size, data);
    return addInstructionTemplate(
        LirOpcodes.NEWARRAYFILLEDDATA,
        Collections.singletonList(payloadConstant),
        Collections.singletonList(src));
  }

  public LirBuilder<V, EV> addNumberConversion(NumericType from, NumericType to, V value) {
    int opcode = new CfNumberConversion(from, to).getAsmOpcode();
    assert LirOpcodes.I2L <= opcode;
    assert opcode <= LirOpcodes.I2S;
    return addOneValueInstruction(opcode, value);
  }

  public LirBuilder<V, EV> addArrayGetObject(DexType destType, V array, V index) {
    return addInstructionTemplate(
        LirOpcodes.AALOAD, Collections.singletonList(destType), ImmutableList.of(array, index));
  }

  public LirBuilder<V, EV> addArrayGetPrimitive(MemberType memberType, V array, V index) {
    int opcode;
    switch (memberType) {
      case BOOLEAN_OR_BYTE:
        opcode = LirOpcodes.BALOAD;
        break;
      case CHAR:
        opcode = LirOpcodes.CALOAD;
        break;
      case SHORT:
        opcode = LirOpcodes.SALOAD;
        break;
      case INT:
        opcode = LirOpcodes.IALOAD;
        break;
      case FLOAT:
        opcode = LirOpcodes.FALOAD;
        break;
      case LONG:
        opcode = LirOpcodes.LALOAD;
        break;
      case DOUBLE:
        opcode = LirOpcodes.DALOAD;
        break;
      default:
        throw new Unreachable("Unexpected object or imprecise member type: " + memberType);
    }
    return addInstructionTemplate(opcode, Collections.emptyList(), ImmutableList.of(array, index));
  }

  public LirBuilder<V, EV> addArrayPut(MemberType memberType, V array, V index, V value) {
    int opcode;
    switch (memberType) {
      case BOOLEAN_OR_BYTE:
        opcode = LirOpcodes.BASTORE;
        break;
      case CHAR:
        opcode = LirOpcodes.CASTORE;
        break;
      case SHORT:
        opcode = LirOpcodes.SASTORE;
        break;
      case INT:
        opcode = LirOpcodes.IASTORE;
        break;
      case FLOAT:
        opcode = LirOpcodes.FASTORE;
        break;
      case LONG:
        opcode = LirOpcodes.LASTORE;
        break;
      case DOUBLE:
        opcode = LirOpcodes.DASTORE;
        break;
      case OBJECT:
        opcode = LirOpcodes.AASTORE;
        break;
      default:
        throw new Unreachable("Unexpected imprecise member type: " + memberType);
    }
    return addInstructionTemplate(
        opcode, Collections.emptyList(), ImmutableList.of(array, index, value));
  }

  public LirBuilder<V, EV> addDexItemBasedConstString(
      DexReference item, NameComputationInfo<?> nameComputationInfo) {
    NameComputationPayload payload = new NameComputationPayload(nameComputationInfo);
    return addInstructionTemplate(
        LirOpcodes.ITEMBASEDCONSTSTRING, ImmutableList.of(item, payload), Collections.emptyList());
  }

  public LirBuilder<V, EV> addNewUnboxedEnumInstance(DexType clazz, int ordinal) {
    advanceInstructionState();
    int operandSize = constantIndexSize(clazz) + ByteUtils.intEncodingSize(ordinal);
    writer.writeInstruction(LirOpcodes.NEWUNBOXEDENUMINSTANCE, operandSize);
    writeConstantIndex(clazz);
    ByteUtils.writeEncodedInt(ordinal, writer::writeOperand);
    return this;
  }

  public LirBuilder<V, EV> addInitClass(DexType clazz) {
    return addOneItemInstruction(LirOpcodes.INITCLASS, clazz);
  }

  public LirBuilder<V, EV> addRecordFieldValues(DexField[] fields, List<V> values) {
    RecordFieldValuesPayload payload = new RecordFieldValuesPayload(fields);
    return addInstructionTemplate(
        LirOpcodes.RECORDFIELDVALUES, Collections.singletonList(payload), values);
  }
}
