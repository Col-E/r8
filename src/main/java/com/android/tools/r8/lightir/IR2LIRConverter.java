// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.lightir;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.CatchHandlers;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.lightir.LIRBuilder.BlockIndexGetter;
import com.android.tools.r8.utils.ListUtils;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class IR2LIRConverter {

  private IR2LIRConverter() {}

  public static LIRCode translate(IRCode irCode, DexItemFactory factory) {
    irCode.traceBlocks();
    Reference2IntMap<BasicBlock> blocks = new Reference2IntOpenHashMap<>();
    Reference2IntMap<Value> values = new Reference2IntOpenHashMap<>();
    int instructionIndex = 0;
    int valueIndex = 0;
    for (BasicBlock block : irCode.blocks) {
      blocks.put(block, instructionIndex);
      for (Phi phi : block.getPhis()) {
        values.put(phi, valueIndex);
        valueIndex++;
        instructionIndex++;
      }
      for (Instruction instruction : block.getInstructions()) {
        if (instruction.hasOutValue()) {
          values.put(instruction.outValue(), valueIndex);
        }
        valueIndex++;
        if (!instruction.isArgument()) {
          instructionIndex++;
        }
      }
    }
    LIRBuilder<Value, BasicBlock> builder =
        new LIRBuilder<Value, BasicBlock>(
                irCode.context().getReference(), values::getInt, blocks::getInt, factory)
            .setMetadata(irCode.metadata());
    BasicBlockIterator blockIt = irCode.listIterator();
    while (blockIt.hasNext()) {
      BasicBlock block = blockIt.next();
      if (block.hasPhis()) {
        // The block order of the predecessors may change, since the LIR does not encode the
        // direct links, the block order is used to determine predecessor order.
        int[] permutation = computePermutation(block.getPredecessors(), blocks::getInt);
        Value[] operands = new Value[block.getPredecessors().size()];
        for (Phi phi : block.getPhis()) {
          permuteOperands(phi.getOperands(), permutation, operands);
          builder.addPhi(phi.getType(), Arrays.asList(operands));
        }
      }
      if (block.hasCatchHandlers()) {
        CatchHandlers<BasicBlock> handlers = block.getCatchHandlers();
        builder.addTryCatchHanders(
            blocks.getInt(block),
            new CatchHandlers<>(
                handlers.getGuards(), ListUtils.map(handlers.getAllTargets(), blocks::getInt)));
      }
      InstructionIterator it = block.iterator();
      while (it.hasNext()) {
        Instruction instruction = it.next();
        builder.setCurrentPosition(instruction.getPosition());
        if (instruction.isGoto()) {
          BasicBlock nextBlock = blockIt.peekNext();
          if (instruction.asGoto().getTarget() == nextBlock) {
            builder.addFallthrough();
          } else {
            instruction.buildLIR(builder);
          }
        } else {
          instruction.buildLIR(builder);
        }
      }
    }
    return builder.build();
  }

  private static void permuteOperands(List<Value> operands, int[] permutation, Value[] output) {
    for (int i = 0; i < operands.size(); i++) {
      Value operand = operands.get(i);
      output[permutation[i]] = operand;
    }
  }

  private static int[] computePermutation(
      List<BasicBlock> originalPredecessors, BlockIndexGetter<BasicBlock> blockIndexGetter) {
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
