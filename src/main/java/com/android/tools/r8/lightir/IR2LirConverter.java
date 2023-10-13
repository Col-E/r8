// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.lightir;

import com.android.tools.r8.graph.bytecodemetadata.BytecodeMetadataProvider;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.CatchHandlers;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ListUtils;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class IR2LirConverter<EV> {

  private final IRCode irCode;
  private final LirEncodingStrategy<Value, EV> strategy;
  BytecodeMetadataProvider bytecodeMetadataProvider;
  private final LirBuilder<Value, EV> builder;

  private IR2LirConverter(
      InternalOptions options,
      IRCode irCode,
      LirEncodingStrategy<Value, EV> strategy,
      BytecodeMetadataProvider bytecodeMetadataProvider) {
    this.irCode = irCode;
    this.strategy = strategy;
    this.bytecodeMetadataProvider = bytecodeMetadataProvider;
    this.builder =
        new LirBuilder<>(
                irCode.context().getReference(),
                irCode.context().getDefinition().isD8R8Synthesized(),
                strategy,
                options)
            .setMetadata(irCode.metadata())
            .prepareForBytecodeInstructionMetadata(bytecodeMetadataProvider.size());
  }

  public static <EV> LirCode<EV> translate(
      IRCode irCode,
      BytecodeMetadataProvider bytecodeMetadataProvider,
      LirEncodingStrategy<Value, EV> strategy,
      InternalOptions options) {
    return new IR2LirConverter<>(options, irCode, strategy, bytecodeMetadataProvider)
        .internalTranslate();
  }

  private void recordBlock(BasicBlock block, int blockIndex) {
    strategy.defineBlock(block, blockIndex);
  }

  private boolean recordPhi(Phi phi, int valueIndex) {
    recordValue(phi, valueIndex);
    return strategy.isPhiInlineInstruction();
  }

  private void recordValue(Value value, int valueIndex) {
    EV encodedValue = strategy.defineValue(value, valueIndex);
    if (value.hasLocalInfo()) {
      builder.setDebugValue(value.getLocalInfo(), encodedValue);
    }
  }

  private LirCode<EV> internalTranslate() {
    irCode.traceBlocks();
    computeBlockAndValueTables();
    computeInstructions();
    return builder.build();
  }

  private void computeInstructions() {
    // The IR instruction index corresponds to the LIR value index which includes arguments and
    // all instructions.
    int currentValueIndex = 0;
    BasicBlockIterator blockIt = irCode.listIterator();
    while (blockIt.hasNext()) {
      BasicBlock block = blockIt.next();
      if (strategy.isPhiInlineInstruction()) {
        currentValueIndex += computePhis(block);
      }
      if (block.hasCatchHandlers()) {
        CatchHandlers<BasicBlock> handlers = block.getCatchHandlers();
        builder.addTryCatchHanders(
            strategy.getBlockIndex(block),
            new CatchHandlers<>(
                handlers.getGuards(),
                ListUtils.map(handlers.getAllTargets(), strategy::getBlockIndex)));
      }
      InstructionIterator it = block.iterator();
      while (it.hasNext()) {
        assert builder.verifyCurrentValueIndex(currentValueIndex);
        Instruction instruction = it.next();
        assert !instruction.hasOutValue()
            || strategy.verifyValueIndex(instruction.outValue(), currentValueIndex);
        builder.setCurrentMetadata(bytecodeMetadataProvider.getMetadata(instruction));
        builder.setCurrentPosition(instruction.getPosition());
        if (!instruction.getDebugValues().isEmpty()) {
          builder.setDebugLocalEnds(currentValueIndex, instruction.getDebugValues());
        }

        if (instruction.isGoto()) {
          BasicBlock nextBlock = blockIt.peekNext();
          if (instruction.asGoto().getTarget() == nextBlock) {
            builder.addFallthrough();
            currentValueIndex++;
            continue;
          }
        }
        instruction.buildLir(builder);
        currentValueIndex++;
      }
      assert builder.verifyCurrentValueIndex(currentValueIndex);
    }
    if (!strategy.isPhiInlineInstruction()) {
      irCode.listIterator().forEachRemaining(this::computePhis);
    }
  }

  private int computePhis(BasicBlock block) {
    int valuesOffset = 0;
    if (block.hasPhis()) {
      // The block order of the predecessors may change, since the LIR does not encode the
      // direct links, the block order is used to determine predecessor order.
      int[] permutation = computePermutation(block.getPredecessors(), strategy::getBlockIndex);
      Value[] operands = new Value[block.getPredecessors().size()];
      for (Phi phi : block.getPhis()) {
        permuteOperands(phi.getOperands(), permutation, operands);
        builder.addPhi(Arrays.asList(operands));
        valuesOffset++;
      }
    }
    return valuesOffset;
  }

  private void computeBlockAndValueTables() {
    int instructionIndex = 0;
    int valueIndex = 0;
    for (BasicBlock block : irCode.blocks) {
      recordBlock(block, instructionIndex);
      for (Phi phi : block.getPhis()) {
        if (recordPhi(phi, valueIndex)) {
          valueIndex++;
          instructionIndex++;
        }
      }
      for (Instruction instruction : block.getInstructions()) {
        if (instruction.hasOutValue()) {
          recordValue(instruction.outValue(), valueIndex);
        }
        valueIndex++;
        if (!instruction.isArgument()) {
          instructionIndex++;
        }
      }
    }
  }

  private static void permuteOperands(List<Value> operands, int[] permutation, Value[] output) {
    for (int i = 0; i < operands.size(); i++) {
      Value operand = operands.get(i);
      output[permutation[i]] = operand;
    }
  }

  private interface BlockIndexGetter {
    int getBlockIndex(BasicBlock block);
  }

  private static int[] computePermutation(
      List<BasicBlock> originalPredecessors, BlockIndexGetter blockIndexGetter) {
    int predecessorCount = originalPredecessors.size();
    // The final predecessor list is sorted by block order.
    List<BasicBlock> sortedPredecessors = new ArrayList<>(originalPredecessors);
    sortedPredecessors.sort(Comparator.comparingInt(blockIndexGetter::getBlockIndex));
    // Since predecessors are not unique, build a map from each unique block to its set of indices.
    Reference2ReferenceMap<BasicBlock, IntList> mapping =
        new Reference2ReferenceOpenHashMap<>(predecessorCount);
    for (int originalIndex = 0; originalIndex < predecessorCount; originalIndex++) {
      BasicBlock predecessor = originalPredecessors.get(originalIndex);
      mapping.computeIfAbsent(predecessor, k -> new IntArrayList(1)).add(originalIndex);
    }
    // Assign an original index to each sorted index.
    int[] permutation = new int[predecessorCount];
    for (int sortedIndex = 0; sortedIndex < predecessorCount; ) {
      BasicBlock predecessor = sortedPredecessors.get(sortedIndex);
      IntList originalIndices = mapping.get(predecessor);
      assert verifySameBlock(sortedPredecessors, sortedIndex, originalIndices.size());
      for (int originalIndex : originalIndices) {
        permutation[originalIndex] = sortedIndex++;
      }
    }
    return permutation;
  }

  private static boolean verifySameBlock(List<BasicBlock> predecessors, int startIndex, int size) {
    if (size == 1) {
      return true;
    }
    BasicBlock block = predecessors.get(startIndex);
    for (int i = startIndex + 1; i < startIndex + size; i++) {
      BasicBlock other = predecessors.get(i);
      assert block == other;
    }
    return true;
  }
}
