// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.lightir;

import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.PrimitiveTypeElement;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.Add;
import com.android.tools.r8.ir.code.Argument;
import com.android.tools.r8.ir.code.ArrayGet;
import com.android.tools.r8.ir.code.ArrayLength;
import com.android.tools.r8.ir.code.ArrayPut;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.CatchHandlers;
import com.android.tools.r8.ir.code.CheckCast;
import com.android.tools.r8.ir.code.Cmp;
import com.android.tools.r8.ir.code.Cmp.Bias;
import com.android.tools.r8.ir.code.ConstClass;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.DebugLocalWrite;
import com.android.tools.r8.ir.code.DebugPosition;
import com.android.tools.r8.ir.code.Div;
import com.android.tools.r8.ir.code.Goto;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.IfType;
import com.android.tools.r8.ir.code.InstanceGet;
import com.android.tools.r8.ir.code.InstanceOf;
import com.android.tools.r8.ir.code.InstancePut;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.IntSwitch;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.InvokeInterface;
import com.android.tools.r8.ir.code.InvokeMultiNewArray;
import com.android.tools.r8.ir.code.InvokeNewArray;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.InvokeSuper;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.Monitor;
import com.android.tools.r8.ir.code.MonitorType;
import com.android.tools.r8.ir.code.MoveException;
import com.android.tools.r8.ir.code.Mul;
import com.android.tools.r8.ir.code.NewArrayEmpty;
import com.android.tools.r8.ir.code.NewArrayFilledData;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.NumberConversion;
import com.android.tools.r8.ir.code.NumberGenerator;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Position.SyntheticPosition;
import com.android.tools.r8.ir.code.Rem;
import com.android.tools.r8.ir.code.Return;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.StaticPut;
import com.android.tools.r8.ir.code.Sub;
import com.android.tools.r8.ir.code.Throw;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.code.Xor;
import com.android.tools.r8.ir.conversion.MethodConversionOptions.MutableMethodConversionOptions;
import com.android.tools.r8.lightir.LirBuilder.IntSwitchPayload;
import com.android.tools.r8.lightir.LirCode.PositionEntry;
import com.android.tools.r8.utils.ListUtils;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class Lir2IRConverter {

  private Lir2IRConverter() {}

  public static <EV> IRCode translate(
      ProgramMethod method,
      LirCode<EV> lirCode,
      LirDecodingStrategy<Value, EV> strategy,
      AppView<?> appView) {
    Parser<EV> parser = new Parser<>(lirCode, method.getReference(), appView, strategy);
    parser.parseArguments(method);
    parser.ensureDebugInfo();
    lirCode.forEach(view -> view.accept(parser));
    return parser.getIRCode(method);
  }

  /**
   * When building IR the structured LIR parser is used to obtain the decoded operand indexes. The
   * below parser subclass handles translation of indexes to SSA values.
   */
  private static class Parser<EV> extends LirParsedInstructionCallback<EV> {

    private static final int ENTRY_BLOCK_INDEX = -1;

    private final AppView<?> appView;
    private final LirCode<EV> code;
    private final LirDecodingStrategy<Value, EV> strategy;
    private final NumberGenerator valueNumberGenerator = new NumberGenerator();
    private final NumberGenerator basicBlockNumberGenerator = new NumberGenerator();

    private final Int2ReferenceMap<BasicBlock> blocks = new Int2ReferenceOpenHashMap<>();

    private BasicBlock currentBlock = null;
    private int nextInstructionIndex = 0;

    private Position currentPosition;
    private PositionEntry nextPositionEntry = null;
    private int nextIndexInPositionsTable = 0;

    public Parser(
        LirCode<EV> code,
        DexMethod method,
        AppView<?> appView,
        LirDecodingStrategy<Value, EV> strategy) {
      super(code);
      this.appView = appView;
      this.code = code;
      this.strategy = strategy;
      // Recreate the preamble position. This is active for arguments and code with no positions.
      currentPosition = SyntheticPosition.builder().setLine(0).setMethod(method).build();
    }

    @Override
    public int getCurrentValueIndex() {
      return nextInstructionIndex + code.getArgumentCount();
    }

    private void closeCurrentBlock() {
      currentBlock = null;
    }

    private void ensureCurrentBlock() {
      // Control instructions must close the block, thus the current block is null iff the
      // instruction denotes a new block.
      if (currentBlock == null) {
        currentBlock = blocks.computeIfAbsent(nextInstructionIndex, k -> new BasicBlock());
        CatchHandlers<Integer> handlers =
            code.getTryCatchTable().getHandlersForBlock(nextInstructionIndex);
        if (handlers != null) {
          List<BasicBlock> targets = ListUtils.map(handlers.getAllTargets(), this::getBasicBlock);
          targets.forEach(currentBlock::link);
          currentBlock.linkCatchSuccessors(handlers.getGuards(), targets);
        }
      } else {
        assert !blocks.containsKey(nextInstructionIndex);
      }
    }

    private void ensureCurrentPosition() {
      if (nextPositionEntry != null
          && nextPositionEntry.fromInstructionIndex <= nextInstructionIndex) {
        currentPosition = nextPositionEntry.position;
        advanceNextPositionEntry();
      }
    }

    private void advanceNextPositionEntry() {
      nextPositionEntry =
          nextIndexInPositionsTable < code.getPositionTable().length
              ? code.getPositionTable()[nextIndexInPositionsTable++]
              : null;
    }

    public void parseArguments(ProgramMethod method) {
      currentBlock = getBasicBlock(ENTRY_BLOCK_INDEX);
      boolean hasReceiverArgument = !method.getDefinition().isStatic();
      assert code.getArgumentCount()
          == method.getParameters().size() + (hasReceiverArgument ? 1 : 0);
      if (hasReceiverArgument) {
        addThisArgument(method.getHolderType());
      }
      int index = hasReceiverArgument ? 1 : 0;
      for (DexType parameter : method.getParameters()) {
        addArgument(parameter, index++);
      }
      // Set up position state after adding arguments.
      advanceNextPositionEntry();
    }

    public void ensureDebugInfo() {
      if (code.getDebugLocalInfoTable() == null) {
        return;
      }
      code.getDebugLocalInfoTable()
          .forEachLocalDefinition(
              (encodedValue, localInfo) -> {
                Value value = getValue(encodedValue);
                if (!value.hasLocalInfo()) {
                  value.setLocalInfo(localInfo);
                }
                assert value.getLocalInfo() == localInfo;
              });
    }

    // TODO(b/270398965): Replace LinkedList.
    @SuppressWarnings("JdkObsolete")
    public IRCode getIRCode(ProgramMethod method) {
      LinkedList<BasicBlock> blockList = new LinkedList<>();
      IntList blockIndices = new IntArrayList(blocks.keySet());
      blockIndices.sort(Integer::compare);
      for (int i = 0; i < blockIndices.size(); i++) {
        BasicBlock block = blocks.get(blockIndices.getInt(i));
        block.setFilled();
        blockList.add(block);
        // LIR has no value-user info so after building is done, removed unused values.
        for (Instruction instruction : block.getInstructions()) {
          if (instruction.hasOutValue()
              && instruction.isInvoke()
              && instruction.hasUnusedOutValue()) {
            instruction.clearOutValue();
          }
        }
      }
      for (int i = 0; i < peekNextInstructionIndex(); ++i) {
        valueNumberGenerator.next();
      }
      return new IRCode(
          appView.options(),
          method,
          Position.syntheticNone(),
          blockList,
          valueNumberGenerator,
          basicBlockNumberGenerator,
          code.getMetadata(),
          method.getOrigin(),
          new MutableMethodConversionOptions(appView.options()));
    }

    public BasicBlock getBasicBlock(int instructionIndex) {
      return blocks.computeIfAbsent(
          instructionIndex,
          k -> {
            BasicBlock block = new BasicBlock();
            block.setNumber(basicBlockNumberGenerator.next());
            return block;
          });
    }

    public Value getValue(EV encodedValue) {
      return strategy.getValue(encodedValue, code.getStrategyInfo());
    }

    public List<Value> getValues(List<EV> indices) {
      List<Value> arguments = new ArrayList<>(indices.size());
      for (int i = 0; i < indices.size(); i++) {
        arguments.add(getValue(indices.get(i)));
      }
      return arguments;
    }

    public int toInstructionIndexInIR(int lirIndex) {
      return lirIndex + code.getArgumentCount();
    }

    public int peekNextInstructionIndex() {
      return nextInstructionIndex;
    }

    public Value getOutValueForNextInstruction(TypeElement type) {
      int valueIndex = toInstructionIndexInIR(peekNextInstructionIndex());
      return strategy.getValueDefinitionForInstructionIndex(
          valueIndex, type, code::getDebugLocalInfo);
    }

    public Phi getPhiForNextInstructionAndAdvanceState(TypeElement type) {
      int instructionIndex = peekNextInstructionIndex();
      int valueIndex = toInstructionIndexInIR(instructionIndex);
      Phi phi =
          strategy.getPhiDefinitionForInstructionIndex(
              valueIndex,
              blockIndex -> getBasicBlockOrEnsureCurrentBlock(blockIndex, instructionIndex),
              type,
              code::getDebugLocalInfo,
              code.getStrategyInfo());
      ensureCurrentPosition();
      ++nextInstructionIndex;
      return phi;
    }

    private BasicBlock getBasicBlockOrEnsureCurrentBlock(int index, int currentInstructionIndex) {
      // If the index is at current or past it ensure the block.
      if (index >= currentInstructionIndex) {
        ensureCurrentBlock();
        return currentBlock;
      }
      // Otherwise we assume the index is an exact block index for an existing block.
      assert blocks.containsKey(index);
      return getBasicBlock(index);
    }

    private void advanceInstructionState() {
      ensureCurrentBlock();
      ensureCurrentPosition();
      ++nextInstructionIndex;
    }

    private void addInstruction(Instruction instruction) {
      int index = toInstructionIndexInIR(peekNextInstructionIndex());
      advanceInstructionState();
      instruction.setPosition(currentPosition);
      currentBlock.getInstructions().add(instruction);
      instruction.setBlock(currentBlock);
      int[] debugEndIndices = code.getDebugLocalEnds(index);
      if (debugEndIndices != null) {
        for (int encodedDebugEndIndex : debugEndIndices) {
          EV debugEndIndex = code.decodeValueIndex(encodedDebugEndIndex, index);
          Value debugValue = getValue(debugEndIndex);
          debugValue.addDebugLocalEnd(instruction);
        }
      }
    }

    private void addThisArgument(DexType type) {
      Argument argument = addArgument(type, 0);
      argument.outValue().markAsThis();
    }

    private Argument addArgument(DexType type, int index) {
      // Arguments are not included in the "instructions" so this does not call "addInstruction"
      // which would otherwise advance the state.
      TypeElement typeElement = type.toTypeElement(appView);
      Value dest =
          strategy.getValueDefinitionForInstructionIndex(
              index, typeElement, code::getDebugLocalInfo);
      Argument argument = new Argument(dest, index, type.isBooleanType());
      assert currentBlock != null;
      assert currentPosition.isSyntheticPosition();
      argument.setPosition(currentPosition);
      currentBlock.getInstructions().add(argument);
      argument.setBlock(currentBlock);
      return argument;
    }

    @Override
    public void onInstruction() {
      throw new Unimplemented("Missing IR conversion");
    }

    @Override
    public void onConstNull() {
      Value dest = getOutValueForNextInstruction(TypeElement.getNull());
      addInstruction(new ConstNumber(dest, 0));
    }

    @Override
    public void onConstInt(int value) {
      Value dest = getOutValueForNextInstruction(TypeElement.getInt());
      addInstruction(new ConstNumber(dest, value));
    }

    @Override
    public void onConstFloat(int value) {
      Value dest = getOutValueForNextInstruction(TypeElement.getFloat());
      addInstruction(new ConstNumber(dest, value));
    }

    @Override
    public void onConstLong(long value) {
      Value dest = getOutValueForNextInstruction(TypeElement.getLong());
      addInstruction(new ConstNumber(dest, value));
    }

    @Override
    public void onConstDouble(long value) {
      Value dest = getOutValueForNextInstruction(TypeElement.getDouble());
      addInstruction(new ConstNumber(dest, value));
    }

    TypeElement valueTypeElement(NumericType type) {
      return PrimitiveTypeElement.fromNumericType(type);
    }

    @Override
    public void onAdd(NumericType type, EV leftValueIndex, EV rightValueIndex) {
      Value dest = getOutValueForNextInstruction(valueTypeElement(type));
      addInstruction(
          Add.createNonNormalized(type, dest, getValue(leftValueIndex), getValue(rightValueIndex)));
    }

    @Override
    public void onSub(NumericType type, EV leftValueIndex, EV rightValueIndex) {
      Value dest = getOutValueForNextInstruction(valueTypeElement(type));
      addInstruction(new Sub(type, dest, getValue(leftValueIndex), getValue(rightValueIndex)));
    }

    @Override
    public void onMul(NumericType type, EV leftValueIndex, EV rightValueIndex) {
      Value dest = getOutValueForNextInstruction(valueTypeElement(type));
      addInstruction(
          Mul.createNonNormalized(type, dest, getValue(leftValueIndex), getValue(rightValueIndex)));
    }

    @Override
    public void onDiv(NumericType type, EV leftValueIndex, EV rightValueIndex) {
      Value dest = getOutValueForNextInstruction(valueTypeElement(type));
      addInstruction(new Div(type, dest, getValue(leftValueIndex), getValue(rightValueIndex)));
    }

    @Override
    public void onRem(NumericType type, EV leftValueIndex, EV rightValueIndex) {
      Value dest = getOutValueForNextInstruction(valueTypeElement(type));
      addInstruction(new Rem(type, dest, getValue(leftValueIndex), getValue(rightValueIndex)));
    }

    @Override
    public void onXor(NumericType type, EV left, EV right) {
      Value dest = getOutValueForNextInstruction(valueTypeElement(type));
      addInstruction(Xor.createNonNormalized(type, dest, getValue(left), getValue(right)));
    }

    @Override
    public void onConstString(DexString string) {
      Value dest = getOutValueForNextInstruction(TypeElement.stringClassType(appView));
      addInstruction(new ConstString(dest, string));
    }

    @Override
    public void onConstClass(DexType type) {
      Value dest =
          getOutValueForNextInstruction(
              type.toTypeElement(appView, Nullability.definitelyNotNull()));
      addInstruction(new ConstClass(dest, type));
    }

    @Override
    public void onNumberConversion(NumericType from, NumericType to, EV value) {
      Value dest =
          getOutValueForNextInstruction(
              to.toDexType(appView.dexItemFactory()).toTypeElement(appView));
      addInstruction(new NumberConversion(from, to, dest, getValue(value)));
    }

    @Override
    public void onIf(IfType ifKind, int blockIndex, EV valueIndex) {
      BasicBlock targetBlock = getBasicBlock(blockIndex);
      Value value = getValue(valueIndex);
      addInstruction(new If(ifKind, value));
      currentBlock.link(targetBlock);
      currentBlock.link(getBasicBlock(nextInstructionIndex));
      closeCurrentBlock();
    }

    @Override
    public void onIfCmp(IfType ifKind, int blockIndex, EV leftValueIndex, EV rightValueIndex) {
      BasicBlock targetBlock = getBasicBlock(blockIndex);
      Value leftValue = getValue(leftValueIndex);
      Value rightValue = getValue(rightValueIndex);
      addInstruction(new If(ifKind, ImmutableList.of(leftValue, rightValue)));
      currentBlock.link(targetBlock);
      currentBlock.link(getBasicBlock(nextInstructionIndex));
      closeCurrentBlock();
    }

    @Override
    public void onFallthrough() {
      int nextBlockIndex = peekNextInstructionIndex() + 1;
      onGoto(nextBlockIndex);
    }

    @Override
    public void onGoto(int blockIndex) {
      BasicBlock targetBlock = getBasicBlock(blockIndex);
      addInstruction(new Goto());
      currentBlock.link(targetBlock);
      closeCurrentBlock();
    }

    @Override
    public void onIntSwitch(EV value, IntSwitchPayload payload) {
      // keys is the 'value' -> 'target index' mapping.
      int[] keys = payload.keys;
      // successorIndices is the 'target index' to 'IR successor index'.
      int[] successorIndices = new int[keys.length];
      List<BasicBlock> successorBlocks = new ArrayList<>();
      {
        // The mapping from instruction to successor is a temp mapping to track if any targets
        // point to the same block.
        Int2IntMap instructionToSuccessor = new Int2IntOpenHashMap();
        for (int i = 0; i < successorIndices.length; i++) {
          int instructionIndex = payload.targets[i];
          if (instructionToSuccessor.containsKey(instructionIndex)) {
            successorIndices[i] = instructionToSuccessor.get(instructionIndex);
          } else {
            int successorIndex = successorBlocks.size();
            successorIndices[i] = successorIndex;
            instructionToSuccessor.put(instructionIndex, successorIndex);
            successorBlocks.add(getBasicBlock(instructionIndex));
          }
        }
      }
      int fallthrough = successorBlocks.size();
      addInstruction(new IntSwitch(getValue(value), keys, successorIndices, fallthrough));
      // The call to addInstruction will ensure the current block so don't amend to it before here.
      // If the block has successors then the index mappings are not valid / need to be offset.
      assert currentBlock.getSuccessors().isEmpty();
      successorBlocks.forEach(currentBlock::link);
      currentBlock.link(getBasicBlock(nextInstructionIndex));
      closeCurrentBlock();
    }

    @Override
    public void onInvokeDirect(DexMethod target, List<EV> arguments, boolean isInterface) {
      Value dest = getInvokeInstructionOutputValue(target);
      List<Value> ssaArgumentValues = getValues(arguments);
      InvokeDirect instruction = new InvokeDirect(target, dest, ssaArgumentValues, isInterface);
      addInstruction(instruction);
    }

    @Override
    public void onInvokeSuper(DexMethod method, List<EV> arguments, boolean isInterface) {
      Value dest = getInvokeInstructionOutputValue(method);
      List<Value> ssaArgumentValues = getValues(arguments);
      InvokeSuper instruction = new InvokeSuper(method, dest, ssaArgumentValues, isInterface);
      addInstruction(instruction);
    }

    @Override
    public void onInvokeVirtual(DexMethod target, List<EV> arguments) {
      Value dest = getInvokeInstructionOutputValue(target);
      List<Value> ssaArgumentValues = getValues(arguments);
      InvokeVirtual instruction = new InvokeVirtual(target, dest, ssaArgumentValues);
      addInstruction(instruction);
    }

    @Override
    public void onInvokeStatic(DexMethod target, List<EV> arguments, boolean isInterface) {
      Value dest = getInvokeInstructionOutputValue(target);
      List<Value> ssaArgumentValues = getValues(arguments);
      InvokeStatic instruction = new InvokeStatic(target, dest, ssaArgumentValues, isInterface);
      addInstruction(instruction);
    }

    @Override
    public void onInvokeInterface(DexMethod target, List<EV> arguments) {
      Value dest = getInvokeInstructionOutputValue(target);
      List<Value> ssaArgumentValues = getValues(arguments);
      InvokeInterface instruction = new InvokeInterface(target, dest, ssaArgumentValues);
      addInstruction(instruction);
    }

    private Value getInvokeInstructionOutputValue(DexMethod target) {
      return target.getReturnType().isVoidType()
          ? null
          : getOutValueForNextInstruction(target.getReturnType().toTypeElement(appView));
    }

    @Override
    public void onNewInstance(DexType clazz) {
      TypeElement type = TypeElement.fromDexType(clazz, Nullability.definitelyNotNull(), appView);
      Value dest = getOutValueForNextInstruction(type);
      addInstruction(new NewInstance(clazz, dest));
    }

    @Override
    public void onStaticGet(DexField field) {
      Value dest = getOutValueForNextInstruction(field.getTypeElement(appView));
      addInstruction(new StaticGet(dest, field));
    }

    @Override
    public void onStaticPut(DexField field, EV value) {
      addInstruction(new StaticPut(getValue(value), field));
    }

    @Override
    public void onInstanceGet(DexField field, EV object) {
      Value dest = getOutValueForNextInstruction(field.getTypeElement(appView));
      addInstruction(new InstanceGet(dest, getValue(object), field));
    }

    @Override
    public void onInstancePut(DexField field, EV object, EV value) {
      addInstruction(new InstancePut(field, getValue(object), getValue(value)));
    }

    @Override
    public void onNewArrayEmpty(DexType type, EV size) {
      Value dest = getOutValueForNextInstruction(type.toTypeElement(appView));
      addInstruction(new NewArrayEmpty(dest, getValue(size), type));
    }

    @Override
    public void onThrow(EV exception) {
      addInstruction(new Throw(getValue(exception)));
      closeCurrentBlock();
    }

    @Override
    public void onReturnVoid() {
      addInstruction(new Return());
      closeCurrentBlock();
    }

    @Override
    public void onReturn(EV value) {
      addInstruction(new Return(getValue(value)));
      closeCurrentBlock();
    }

    @Override
    public void onArrayLength(EV arrayValueIndex) {
      Value dest = getOutValueForNextInstruction(TypeElement.getInt());
      Value arrayValue = getValue(arrayValueIndex);
      addInstruction(new ArrayLength(dest, arrayValue));
    }

    @Override
    public void onCheckCast(DexType type, EV value) {
      Value dest = getOutValueForNextInstruction(type.toTypeElement(appView));
      addInstruction(new CheckCast(dest, getValue(value), type));
    }

    @Override
    public void onInstanceOf(DexType type, EV value) {
      Value dest = getOutValueForNextInstruction(TypeElement.getInt());
      addInstruction(new InstanceOf(dest, getValue(value), type));
    }

    @Override
    public void onDebugPosition() {
      addInstruction(new DebugPosition());
    }

    @Override
    public void onPhi(DexType type, List<EV> operands) {
      Phi phi = getPhiForNextInstructionAndAdvanceState(type.toTypeElement(appView));
      List<Value> values = new ArrayList<>(operands.size());
      for (int i = 0; i < operands.size(); i++) {
        values.add(getValue(operands.get(i)));
      }
      phi.addOperands(values);
    }

    @Override
    public void onMoveException(DexType exceptionType) {
      Value dest = getOutValueForNextInstruction(exceptionType.toTypeElement(appView));
      addInstruction(new MoveException(dest, exceptionType, appView.options()));
    }

    @Override
    public void onDebugLocalWrite(EV srcIndex) {
      Value src = getValue(srcIndex);
      // The type is in the local table, so initialize it with bottom and reset with the local info.
      Value dest = getOutValueForNextInstruction(TypeElement.getBottom());
      TypeElement type = dest.getLocalInfo().type.toTypeElement(appView);
      dest.setType(type);
      addInstruction(new DebugLocalWrite(dest, src));
    }

    @Override
    public void onInvokeMultiNewArray(DexType type, List<EV> arguments) {
      Value dest =
          getOutValueForNextInstruction(
              type.toTypeElement(appView, Nullability.definitelyNotNull()));
      addInstruction(new InvokeMultiNewArray(type, dest, getValues(arguments)));
    }

    @Override
    public void onInvokeNewArray(DexType type, List<EV> arguments) {
      Value dest =
          getOutValueForNextInstruction(
              type.toTypeElement(appView, Nullability.definitelyNotNull()));
      addInstruction(new InvokeNewArray(type, dest, getValues(arguments)));
    }

    @Override
    public void onNewArrayFilledData(int elementWidth, long size, short[] data, EV src) {
      addInstruction(new NewArrayFilledData(getValue(src), elementWidth, size, data));
    }

    @Override
    public void onCmpInstruction(int opcode, EV leftIndex, EV rightIndex) {
      NumericType type;
      Bias bias;
      switch (opcode) {
        case LirOpcodes.LCMP:
          type = NumericType.LONG;
          bias = Bias.NONE;
          break;
        case LirOpcodes.FCMPL:
          type = NumericType.FLOAT;
          bias = Bias.LT;
          break;
        case LirOpcodes.FCMPG:
          type = NumericType.FLOAT;
          bias = Bias.GT;
          break;
        case LirOpcodes.DCMPL:
          type = NumericType.DOUBLE;
          bias = Bias.LT;
          break;
        case LirOpcodes.DCMPG:
          type = NumericType.DOUBLE;
          bias = Bias.GT;
          break;
        default:
          throw new Unreachable("Unexpected cmp opcode: " + opcode);
      }
      Value leftValue = getValue(leftIndex);
      Value rightValue = getValue(rightIndex);
      Value dest = getOutValueForNextInstruction(TypeElement.getInt());
      addInstruction(new Cmp(type, bias, dest, leftValue, rightValue));
    }

    @Override
    public void onMonitorEnter(EV value) {
      addInstruction(new Monitor(MonitorType.ENTER, getValue(value)));
    }

    @Override
    public void onMonitorExit(EV value) {
      addInstruction(new Monitor(MonitorType.EXIT, getValue(value)));
    }

    @Override
    public void onArrayGetObject(DexType type, EV array, EV index) {
      Value dest = getOutValueForNextInstruction(type.toTypeElement(appView));
      addInstruction(new ArrayGet(MemberType.OBJECT, dest, getValue(array), getValue(index)));
    }

    @Override
    public void onArrayGetPrimitive(MemberType type, EV array, EV index) {
      // Convert the member type to a "stack value type", e.g., byte, char etc to int.
      ValueType valueType = ValueType.fromMemberType(type);
      DexType dexType = valueType.toDexType(appView.dexItemFactory());
      Value dest = getOutValueForNextInstruction(dexType.toTypeElement(appView));
      addInstruction(new ArrayGet(type, dest, getValue(array), getValue(index)));
    }

    @Override
    public void onArrayPut(MemberType type, EV array, EV index, EV value) {
      addInstruction(
          ArrayPut.createWithoutVerification(
              type, getValue(array), getValue(index), getValue(value)));
    }
  }
}
