// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.lightir;

import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.proto.ArgumentInfo;
import com.android.tools.r8.graph.proto.ArgumentInfoCollection;
import com.android.tools.r8.graph.proto.RemovedArgumentInfo;
import com.android.tools.r8.graph.proto.RewrittenPrototypeDescription;
import com.android.tools.r8.graph.proto.RewrittenTypeInfo;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.PrimitiveTypeElement;
import com.android.tools.r8.ir.analysis.type.TypeAnalysis;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.Add;
import com.android.tools.r8.ir.code.And;
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
import com.android.tools.r8.ir.code.ConstMethodHandle;
import com.android.tools.r8.ir.code.ConstMethodType;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.DebugLocalRead;
import com.android.tools.r8.ir.code.DebugLocalWrite;
import com.android.tools.r8.ir.code.DebugPosition;
import com.android.tools.r8.ir.code.DexItemBasedConstString;
import com.android.tools.r8.ir.code.Div;
import com.android.tools.r8.ir.code.Goto;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.IfType;
import com.android.tools.r8.ir.code.InitClass;
import com.android.tools.r8.ir.code.InstanceGet;
import com.android.tools.r8.ir.code.InstanceOf;
import com.android.tools.r8.ir.code.InstancePut;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.IntSwitch;
import com.android.tools.r8.ir.code.InvokeCustom;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.InvokeInterface;
import com.android.tools.r8.ir.code.InvokeMultiNewArray;
import com.android.tools.r8.ir.code.InvokePolymorphic;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.InvokeSuper;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.Monitor;
import com.android.tools.r8.ir.code.MonitorType;
import com.android.tools.r8.ir.code.MoveException;
import com.android.tools.r8.ir.code.Mul;
import com.android.tools.r8.ir.code.Neg;
import com.android.tools.r8.ir.code.NewArrayEmpty;
import com.android.tools.r8.ir.code.NewArrayFilled;
import com.android.tools.r8.ir.code.NewArrayFilledData;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.NewUnboxedEnumInstance;
import com.android.tools.r8.ir.code.Not;
import com.android.tools.r8.ir.code.NumberConversion;
import com.android.tools.r8.ir.code.NumberGenerator;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.code.Or;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.RecordFieldValues;
import com.android.tools.r8.ir.code.Rem;
import com.android.tools.r8.ir.code.Return;
import com.android.tools.r8.ir.code.SafeCheckCast;
import com.android.tools.r8.ir.code.Shl;
import com.android.tools.r8.ir.code.Shr;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.StaticPut;
import com.android.tools.r8.ir.code.StringSwitch;
import com.android.tools.r8.ir.code.Sub;
import com.android.tools.r8.ir.code.Throw;
import com.android.tools.r8.ir.code.Ushr;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.code.Xor;
import com.android.tools.r8.ir.conversion.ExtraParameter;
import com.android.tools.r8.ir.conversion.MethodConversionOptions.MutableMethodConversionOptions;
import com.android.tools.r8.ir.conversion.StringSwitchConverter;
import com.android.tools.r8.lightir.LirBuilder.IntSwitchPayload;
import com.android.tools.r8.lightir.LirBuilder.StringSwitchPayload;
import com.android.tools.r8.lightir.LirCode.PositionEntry;
import com.android.tools.r8.lightir.LirCode.TryCatchTable;
import com.android.tools.r8.naming.dexitembasedstring.NameComputationInfo;
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
import java.util.function.BiFunction;

public class Lir2IRConverter {

  private Lir2IRConverter() {}

  public static <EV> IRCode translate(
      ProgramMethod method,
      LirCode<EV> lirCode,
      LirDecodingStrategy<Value, EV> strategy,
      AppView<?> appView,
      Position callerPosition,
      RewrittenPrototypeDescription protoChanges,
      MutableMethodConversionOptions conversionOptions) {
    Parser<EV> parser =
        new Parser<>(
            lirCode,
            method.getReference(),
            method.getDefinition().isD8R8Synthesized(),
            appView,
            strategy,
            callerPosition,
            protoChanges);
    parser.parseArguments(method);
    parser.ensureDebugInfo();
    lirCode.forEach(view -> view.accept(parser));
    IRCode irCode = parser.getIRCode(method, conversionOptions);
    // Some instructions have bottom types (e.g., phis). Compute their actual types by widening.
    new TypeAnalysis(appView, irCode).widening();

    if (conversionOptions.isStringSwitchConversionEnabled()) {
      if (StringSwitchConverter.convertToStringSwitchInstructions(
          irCode, appView.dexItemFactory())) {
        irCode.removeRedundantBlocks();
      }
    }
    return irCode;
  }

  /**
   * When building IR the structured LIR parser is used to obtain the decoded operand indexes. The
   * below parser subclass handles translation of indexes to SSA values.
   */
  private static class Parser<EV> extends LirParsedInstructionCallback<EV> {

    private static final int ENTRY_BLOCK_INDEX = -1;

    private final AppView<?> appView;
    private final LirCode<EV> code;
    private final DexMethod method;
    private final LirDecodingStrategy<Value, EV> strategy;
    private final NumberGenerator basicBlockNumberGenerator = new NumberGenerator();
    private final RewrittenPrototypeDescription protoChanges;

    private final Int2ReferenceMap<BasicBlock> blocks = new Int2ReferenceOpenHashMap<>();

    private BasicBlock currentBlock = null;
    private int nextInstructionIndex = 0;

    private final Position entryPosition;
    private Position currentPosition;
    private PositionEntry nextPositionEntry = null;
    private int nextIndexInPositionsTable = 0;
    private final PositionEntry[] positionTable;

    private final boolean buildForInlining;

    public Parser(
        LirCode<EV> code,
        DexMethod method,
        boolean isD8R8Synthesized,
        AppView<?> appView,
        LirDecodingStrategy<Value, EV> strategy,
        Position callerPosition,
        RewrittenPrototypeDescription protoChanges) {
      super(code);
      this.appView = appView;
      this.code = code;
      this.method = method;
      this.strategy = strategy;
      this.protoChanges = protoChanges;
      assert protoChanges != null;
      if (callerPosition == null) {
        buildForInlining = false;
        positionTable = code.getPositionTable();
        // Recreate the preamble position. This is active for arguments and code with no positions.
        currentPosition = code.getPreamblePosition(method, isD8R8Synthesized);
      } else {
        buildForInlining = true;
        positionTable =
            code.getPositionTableAsInlining(
                callerPosition, method, isD8R8Synthesized, preamble -> currentPosition = preamble);
      }
      entryPosition = currentPosition;
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
        currentBlock = getBasicBlock(nextInstructionIndex);
        TryCatchTable tryCatchTable = code.getTryCatchTable();
        if (tryCatchTable != null) {
          CatchHandlers<Integer> handlers = tryCatchTable.getHandlersForBlock(nextInstructionIndex);
          if (handlers != null) {
            List<BasicBlock> targets = ListUtils.map(handlers.getAllTargets(), this::getBasicBlock);
            targets.forEach(currentBlock::link);
            currentBlock.linkCatchSuccessors(handlers.getGuards(), targets);
          }
        }
      } else {
        assert !blocks.containsKey(nextInstructionIndex);
      }
    }

    private void ensureCurrentPosition() {
      if (nextPositionEntry != null
          && nextPositionEntry.getFromInstructionIndex() <= nextInstructionIndex) {
        currentPosition =
            nextPositionEntry.getPosition(
                method, entryPosition.getOutermostCaller().isD8R8Synthesized());
        advanceNextPositionEntry();
      }
    }

    private void advanceNextPositionEntry() {
      nextPositionEntry =
          nextIndexInPositionsTable < positionTable.length
              ? positionTable[nextIndexInPositionsTable++]
              : null;
    }

    @SuppressWarnings("ReferenceEquality")
    public void parseArguments(ProgramMethod method) {
      ArgumentInfoCollection argumentsInfo = protoChanges.getArgumentInfoCollection();
      currentBlock = getBasicBlock(ENTRY_BLOCK_INDEX);
      boolean hasReceiverArgument = !method.getDefinition().isStatic();

      int index = 0;
      if (hasReceiverArgument) {
        assert argumentsInfo.getNewArgumentIndex(0) == 0;
        addThisArgument(method.getHolderType());
        index++;
      }

      int originalNumberOfArguments =
          method.getParameters().size()
              + argumentsInfo.numberOfRemovedArguments()
              + method.getDefinition().getFirstNonReceiverArgumentIndex()
              - protoChanges.numberOfExtraParameters();

      int numberOfRemovedArguments = 0;
      while (index < originalNumberOfArguments) {
        ArgumentInfo argumentInfo = argumentsInfo.getArgumentInfo(index);
        if (argumentInfo.isRemovedArgumentInfo()) {
          RemovedArgumentInfo removedArgumentInfo = argumentInfo.asRemovedArgumentInfo();
          addNonThisArgument(removedArgumentInfo.getType(), index++);
          numberOfRemovedArguments++;
        } else if (argumentInfo.isRewrittenTypeInfo()) {
          RewrittenTypeInfo rewrittenTypeInfo = argumentInfo.asRewrittenTypeInfo();
          int newArgumentIndex = argumentsInfo.getNewArgumentIndex(index, numberOfRemovedArguments);
          assert method.getArgumentType(newArgumentIndex) == rewrittenTypeInfo.getNewType();
          addNonThisArgument(rewrittenTypeInfo.getOldType(), index++);
        } else {
          int newArgumentIndex = argumentsInfo.getNewArgumentIndex(index, numberOfRemovedArguments);
          addNonThisArgument(method.getArgumentType(newArgumentIndex), index++);
        }
      }

      for (ExtraParameter extraParameter : protoChanges.getExtraParameters()) {
        int newArgumentIndex = argumentsInfo.getNewArgumentIndex(index, numberOfRemovedArguments);
        DexType extraArgumentType = method.getArgumentType(newArgumentIndex);
        if (extraParameter.isUnused()) {
          // Note that we do *not* increment the index here as that would shift the SSA value map.
          addUnusedArgument(extraArgumentType);
        } else {
          addNonThisArgument(extraArgumentType, index++);
        }
      }

      // Set up position state after adding arguments.
      advanceNextPositionEntry();
    }

    @SuppressWarnings("ReferenceEquality")
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
    public IRCode getIRCode(
        ProgramMethod method, MutableMethodConversionOptions conversionOptions) {
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
      return new IRCode(
          appView.options(),
          method,
          entryPosition,
          blockList,
          strategy.getValueNumberGenerator(),
          basicBlockNumberGenerator,
          code.getMetadataForIR(),
          method.getOrigin(),
          conversionOptions);
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
      boolean receiverCouldBeNull = buildForInlining;
      Nullability nullability =
          receiverCouldBeNull ? Nullability.maybeNull() : Nullability.definitelyNotNull();
      TypeElement typeElement = type.toTypeElement(appView, nullability);
      Value dest =
          strategy.getValueDefinitionForInstructionIndex(0, typeElement, code::getDebugLocalInfo);
      Argument argument = internalAddArgument(dest, false);
      argument.outValue().markAsThis();
    }

    private void addNonThisArgument(DexType type, int index) {
      TypeElement typeElement = type.toTypeElement(appView);
      Value dest =
          strategy.getValueDefinitionForInstructionIndex(
              index, typeElement, code::getDebugLocalInfo);
      internalAddArgument(dest, type.isBooleanType());
    }

    private void addUnusedArgument(DexType type) {
      // Extra unused null arguments don't have valid indexes in LIR and must not adjust existing
      // indexes.
      TypeElement typeElement =
          type.isReferenceType() ? TypeElement.getNull() : type.toTypeElement(appView);
      Value dest = strategy.getFreshUnusedValue(typeElement);
      internalAddArgument(dest, false);
    }

    private Argument internalAddArgument(Value dest, boolean isBooleanType) {
      assert currentBlock != null;
      // Arguments are not included in the "instructions" so this does not call "addInstruction"
      // which would otherwise advance the state.
      Argument argument = new Argument(dest, currentBlock.size(), isBooleanType);
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
    public void onNeg(NumericType type, EV value) {
      Value dest = getOutValueForNextInstruction(valueTypeElement(type));
      addInstruction(new Neg(type, dest, getValue(value)));
    }

    @Override
    public void onNot(NumericType type, EV value) {
      Value dest = getOutValueForNextInstruction(valueTypeElement(type));
      addInstruction(new Not(type, dest, getValue(value)));
    }

    @Override
    public void onShl(NumericType type, EV left, EV right) {
      Value dest = getOutValueForNextInstruction(valueTypeElement(type));
      addInstruction(new Shl(type, dest, getValue(left), getValue(right)));
    }

    @Override
    public void onShr(NumericType type, EV left, EV right) {
      Value dest = getOutValueForNextInstruction(valueTypeElement(type));
      addInstruction(new Shr(type, dest, getValue(left), getValue(right)));
    }

    @Override
    public void onUshr(NumericType type, EV left, EV right) {
      Value dest = getOutValueForNextInstruction(valueTypeElement(type));
      addInstruction(new Ushr(type, dest, getValue(left), getValue(right)));
    }

    @Override
    public void onAnd(NumericType type, EV left, EV right) {
      Value dest = getOutValueForNextInstruction(valueTypeElement(type));
      addInstruction(And.createNonNormalized(type, dest, getValue(left), getValue(right)));
    }

    @Override
    public void onOr(NumericType type, EV left, EV right) {
      Value dest = getOutValueForNextInstruction(valueTypeElement(type));
      addInstruction(Or.createNonNormalized(type, dest, getValue(left), getValue(right)));
    }

    @Override
    public void onXor(NumericType type, EV left, EV right) {
      Value dest = getOutValueForNextInstruction(valueTypeElement(type));
      addInstruction(Xor.createNonNormalized(type, dest, getValue(left), getValue(right)));
    }

    @Override
    public void onConstString(DexString string) {
      Value dest =
          getOutValueForNextInstruction(
              TypeElement.stringClassType(appView, Nullability.definitelyNotNull()));
      addInstruction(new ConstString(dest, string));
    }

    @Override
    public void onDexItemBasedConstString(
        DexReference item, NameComputationInfo<?> nameComputationInfo) {
      Value dest =
          getOutValueForNextInstruction(
              TypeElement.stringClassType(appView, Nullability.definitelyNotNull()));
      addInstruction(new DexItemBasedConstString(dest, item, nameComputationInfo));
    }

    @Override
    public void onConstClass(DexType type, boolean ignoreCompatRules) {
      Value dest =
          getOutValueForNextInstruction(
              TypeElement.classClassType(appView, Nullability.definitelyNotNull()));
      addInstruction(new ConstClass(dest, type, ignoreCompatRules));
    }

    @Override
    public void onConstMethodHandle(DexMethodHandle methodHandle) {
      TypeElement handleType =
          TypeElement.fromDexType(
              appView.dexItemFactory().methodHandleType, Nullability.definitelyNotNull(), appView);
      Value dest = getOutValueForNextInstruction(handleType);
      addInstruction(new ConstMethodHandle(dest, methodHandle));
    }

    @Override
    public void onConstMethodType(DexProto methodType) {
      TypeElement typeElement =
          TypeElement.fromDexType(
              appView.dexItemFactory().methodTypeType, Nullability.definitelyNotNull(), appView);
      Value dest = getOutValueForNextInstruction(typeElement);
      addInstruction(new ConstMethodType(dest, methodType));
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
      addSwitchInstruction(
          payload.targets,
          (successors, fallthrough) ->
              new IntSwitch(getValue(value), keys, successors, fallthrough));
    }

    @Override
    public void onStringSwitch(EV value, StringSwitchPayload payload) {
      int size = payload.keys.length;
      DexString[] keys = new DexString[size];
      for (int i = 0; i < size; i++) {
        keys[i] = (DexString) code.getConstantItem(payload.keys[i]);
      }
      addSwitchInstruction(
          payload.targets,
          (successors, fallthrough) ->
              new StringSwitch(getValue(value), keys, successors, fallthrough));
    }

    private void addSwitchInstruction(
        int[] targets, BiFunction<int[], Integer, Instruction> createSwitchInstruction) {
      int size = targets.length;
      // successorIndices is the 'target index' to 'IR successor index'.
      int[] successorIndices = new int[size];
      List<BasicBlock> successorBlocks = new ArrayList<>(size);
      // The mapping from instruction to successor is a temp mapping to track if any targets
      // point to the same block.
      Int2IntMap instructionToSuccessor = new Int2IntOpenHashMap(size);
      for (int i = 0; i < targets.length; i++) {
        int instructionIndex = targets[i];
        if (instructionToSuccessor.containsKey(instructionIndex)) {
          successorIndices[i] = instructionToSuccessor.get(instructionIndex);
        } else {
          int successorIndex = successorBlocks.size();
          successorIndices[i] = successorIndex;
          instructionToSuccessor.put(instructionIndex, successorIndex);
          successorBlocks.add(getBasicBlock(instructionIndex));
        }
      }
      int fallthrough = successorBlocks.size();
      addInstruction(createSwitchInstruction.apply(successorIndices, fallthrough));
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

    @Override
    public void onInvokeCustom(DexCallSite callSite, List<EV> arguments) {
      // The actual type of invoke custom may have multiple interface types. Defer type to widening.
      Value dest = getOutValueForNextInstruction(TypeElement.getBottom());
      List<Value> ssaArgumentValues = getValues(arguments);
      InvokeCustom instruction = new InvokeCustom(callSite, dest, ssaArgumentValues);
      addInstruction(instruction);
    }

    @Override
    public void onInvokePolymorphic(DexMethod target, DexProto proto, List<EV> arguments) {
      Value dest = getInvokeInstructionOutputValue(proto);
      List<Value> ssaArgumentValues = getValues(arguments);
      InvokePolymorphic instruction = new InvokePolymorphic(target, proto, dest, ssaArgumentValues);
      addInstruction(instruction);
    }

    private Value getInvokeInstructionOutputValue(DexMethod target) {
      return getInvokeInstructionOutputValue(target.getProto());
    }

    private Value getInvokeInstructionOutputValue(DexProto target) {
      DexType returnType = target.getReturnType();
      return returnType.isVoidType()
          ? null
          : getOutValueForNextInstruction(returnType.toTypeElement(appView));
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
      addInstruction(
          InstancePut.createPotentiallyInvalid(field, getValue(object), getValue(value)));
    }

    @Override
    public void onNewArrayEmpty(DexType type, EV size) {
      Value dest =
          getOutValueForNextInstruction(
              type.toTypeElement(appView, Nullability.definitelyNotNull()));
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
      if (protoChanges.hasBeenChangedToReturnVoid()) {
        onReturnVoid();
      } else {
        addInstruction(new Return(getValue(value)));
        closeCurrentBlock();
      }
    }

    @Override
    public void onArrayLength(EV arrayValueIndex) {
      Value dest = getOutValueForNextInstruction(TypeElement.getInt());
      Value arrayValue = getValue(arrayValueIndex);
      addInstruction(new ArrayLength(dest, arrayValue));
    }

    @Override
    public void onCheckCast(DexType type, EV value, boolean ignoreCompatRules) {
      Value dest = getOutValueForNextInstruction(type.toTypeElement(appView, Nullability.bottom()));
      addInstruction(new CheckCast(dest, getValue(value), type, ignoreCompatRules));
    }

    @Override
    public void onSafeCheckCast(DexType type, EV value) {
      Value dest = getOutValueForNextInstruction(type.toTypeElement(appView, Nullability.bottom()));
      addInstruction(new SafeCheckCast(dest, getValue(value), type));
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
    public void onPhi(List<EV> operands) {
      // The type of the phi is determined by its operands during type widening.
      Phi phi = getPhiForNextInstructionAndAdvanceState(TypeElement.getBottom());
      List<Value> values = new ArrayList<>(operands.size());
      for (int i = 0; i < operands.size(); i++) {
        values.add(getValue(operands.get(i)));
      }
      phi.addOperands(values, false);
    }

    @Override
    public void onMoveException(DexType exceptionType) {
      Value dest =
          getOutValueForNextInstruction(
              exceptionType.toTypeElement(appView, Nullability.definitelyNotNull()));
      addInstruction(new MoveException(dest, exceptionType, appView.options()));
    }

    @Override
    public void onDebugLocalWrite(EV srcIndex) {
      // The type is dependent on the source so type widening will determine it.
      Value dest = getOutValueForNextInstruction(TypeElement.getBottom());
      addInstruction(new DebugLocalWrite(dest, getValue(srcIndex)));
    }

    @Override
    public void onDebugLocalRead() {
      addInstruction(new DebugLocalRead());
    }

    @Override
    public void onInvokeMultiNewArray(DexType type, List<EV> arguments) {
      Value dest =
          getOutValueForNextInstruction(
              type.toTypeElement(appView, Nullability.definitelyNotNull()));
      addInstruction(new InvokeMultiNewArray(type, dest, getValues(arguments)));
    }

    @Override
    public void onNewArrayFilled(DexType type, List<EV> arguments) {
      Value dest =
          getOutValueForNextInstruction(
              type.toTypeElement(appView, Nullability.definitelyNotNull()));
      addInstruction(new NewArrayFilled(type, dest, getValues(arguments)));
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
    public void onArrayGet(MemberType type, EV array, EV index) {
      TypeElement typeElement;
      if (type.isObject()) {
        // The actual object type must be computed from its array value.
        typeElement = TypeElement.getBottom();
      } else {
        // Convert the member type to a "stack value type", e.g., byte, char etc to int.
        ValueType valueType = ValueType.fromMemberType(type);
        DexType dexType = valueType.toDexType(appView.dexItemFactory());
        typeElement = dexType.toTypeElement(appView);
      }
      Value dest = getOutValueForNextInstruction(typeElement);
      addInstruction(new ArrayGet(type, dest, getValue(array), getValue(index)));
    }

    @Override
    public void onArrayPut(MemberType type, EV array, EV index, EV value) {
      addInstruction(
          ArrayPut.createWithoutVerification(
              type, getValue(array), getValue(index), getValue(value)));
    }

    @Override
    public void onNewUnboxedEnumInstance(DexType clazz, int ordinal) {
      TypeElement type = TypeElement.fromDexType(clazz, Nullability.definitelyNotNull(), appView);
      Value dest = getOutValueForNextInstruction(type);
      addInstruction(new NewUnboxedEnumInstance(clazz, ordinal, dest));
    }

    @Override
    public void onInitClass(DexType clazz) {
      Value dest = getOutValueForNextInstruction(TypeElement.getInt());
      addInstruction(new InitClass(dest, clazz));
    }

    @Override
    public void onRecordFieldValues(DexField[] fields, List<EV> values) {
      TypeElement typeElement =
          TypeElement.fromDexType(
              appView.dexItemFactory().objectArrayType, Nullability.definitelyNotNull(), appView);
      Value dest = getOutValueForNextInstruction(typeElement);
      addInstruction(new RecordFieldValues(fields, dest, getValues(values)));
    }
  }
}
