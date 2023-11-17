// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.ArrayPut;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.NewArrayEmpty;
import com.android.tools.r8.ir.code.NewArrayFilled;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.Value;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class ValueUtils {
  // We allocate an array of this size, so guard against it getting too big.
  private static int MAX_ARRAY_SIZE = 100000;
  private static boolean DEBUG =
      System.getProperty("com.android.tools.r8.debug.computeSingleUseArrayValues") != null;

  @SuppressWarnings("ReferenceEquality")
  public static boolean isStringBuilder(Value value, DexItemFactory dexItemFactory) {
    TypeElement type = value.getType();
    return type.isClassType()
        && type.asClassType().getClassType() == dexItemFactory.stringBuilderType;
  }

  @SuppressWarnings("ReferenceEquality")
  public static boolean isNonNullStringBuilder(Value value, DexItemFactory dexItemFactory) {
    while (true) {
      if (value.isPhi()) {
        return false;
      }

      Instruction definition = value.getDefinition();
      if (definition.isNewInstance()) {
        NewInstance newInstance = definition.asNewInstance();
        return newInstance.clazz == dexItemFactory.stringBuilderType;
      }

      if (definition.isInvokeVirtual()) {
        InvokeVirtual invoke = definition.asInvokeVirtual();
        if (dexItemFactory.stringBuilderMethods.isAppendMethod(invoke.getInvokedMethod())) {
          value = invoke.getReceiver();
          continue;
        }
      }

      // Unhandled definition.
      return false;
    }
  }

  public static final class ArrayValues {
    private List<Value> elementValues;
    private ArrayPut[] arrayPutsByIndex;

    private ArrayValues(List<Value> elementValues) {
      this.elementValues = elementValues;
    }

    private ArrayValues(ArrayPut[] arrayPutsByIndex) {
      this.arrayPutsByIndex = arrayPutsByIndex;
    }

    /** May contain null entries when array has null entries. */
    public List<Value> getElementValues() {
      if (elementValues == null) {
        ArrayPut[] puts = arrayPutsByIndex;
        Value[] elementValuesArr = new Value[puts.length];
        for (int i = 0; i < puts.length; ++i) {
          ArrayPut arrayPut = puts[i];
          elementValuesArr[i] = arrayPut == null ? null : arrayPut.value();
        }
        elementValues = Arrays.asList(elementValuesArr);
      }
      return elementValues;
    }

    public int size() {
      return elementValues != null ? elementValues.size() : arrayPutsByIndex.length;
    }
  }

  private static BasicBlock findNextUnseen(
      int startIdx, List<BasicBlock> predecessors, Set<BasicBlock> seen) {
    int size = predecessors.size();
    for (int i = startIdx; i < size; ++i) {
      BasicBlock next = predecessors.get(i);
      if (!seen.contains(next)) {
        return next;
      }
    }
    return null;
  }

  private static void debugLog(IRCode code, String message) {
    System.err.println(message + " method=" + code.context().getReference());
  }

  /**
   * Returns all dominator blocks of destBlock between (and including) it and sourceBlock. Returns
   * null if sourceBlock is not a dominator of destBlock. Returns null if the algorithm is taking
   * too long.
   */
  private static Set<BasicBlock> computeSimpleCaseDominatorBlocks(
      BasicBlock sourceBlock, BasicBlock destBlock, IRCode code) {
    // Fast-path: blocks are the same.
    // As of Nov 2023 in Chrome for String.format() optimization, this is hit 77% of the time .
    if (destBlock == sourceBlock) {
      if (DEBUG) {
        debugLog(code, "computeSimpleCaseDominatorBlocks: SAME BLOCK");
      }
      return Collections.singleton(sourceBlock);
    }

    // Fast-path: Linear path from source -> dest.
    // As of Nov 2023 in Chrome for String.format() optimization, this is hit 14% of the time .
    BasicBlock curBlock = destBlock;
    List<BasicBlock> curPredecessors;
    Set<BasicBlock> ret = Sets.newIdentityHashSet();
    while (true) {
      curPredecessors = curBlock.getPredecessors();
      ret.add(curBlock);
      if (curBlock == sourceBlock) {
        if (DEBUG) {
          debugLog(code, "computeSimpleCaseDominatorBlocks: LINEAR PATH");
        }
        return ret;
      }

      BasicBlock nextBlock = findNextUnseen(0, curPredecessors, ret);
      if (nextBlock == null) {
        debugLog(code, "computeSimpleCaseDominatorBlocks: Not a dominator.");
        return null;
      }
      if (findNextUnseen(curPredecessors.indexOf(nextBlock) + 1, curPredecessors, ret) != null) {
        // Multiple predecessors.
        break;
      }

      curBlock = nextBlock;
    }

    // Algorithm: Traverse all predecessor paths between curBlock and sourceBlock, tracking the
    // number of times each block is visited.
    // Returns blocks with a "visit count" equal to the number of paths.
    // This algorithm has worst case exponential complexity (for a fully connected graph).
    // Rather than falling back to a DominatorTree for complex cases, this currently just returns
    // an empty set when the number of paths is not small. Thus, it should not be used when false
    // negatives are not acceptable.
    //
    // As of Nov 2023 in Chrome for String.format() optimization, the totalPathCounts were:
    // 2 * 12, 3 * 6, 4 * 2, 5 * 1, 12 * 1, 22 * 1, 24 * 1, 35 * 4,
    // MAX_PATH_COUNT * 2 (even with MAX_PATH_COUNT=3200)
    final int MAX_PATH_COUNT = 36;

    // TODO(agrieve): Lower MAX_PATH_COUNT and use DominatorTree for complicated cases. Or...
    //     Algorithm vNext:
    //     Track the fraction of paths that reach each node. Doing so would be ~linear for graphs
    //     without cycles. E.g.:
    //       DestNodeValue=1
    //       NodeValue=SUM(block.NodeValue / block.numSuccessors for block in successors)
    //       Dominators=(b for b in blocks if b.NodeValue == 1)
    //     To choose which block to visit next, choose any where all predecessors have already been
    //     visited. If no block has all predecessors visited, then an unvisited block is from a
    //     cycle (they cannot be from outside of the sourceBlock->destBlock subgraph so long as
    //     sourceBlock dominates destBlock).
    //     To deal with cycles (clearly not linear time now):
    //     * Find all blocks that participate in a cycle by doing a full traversal starting from
    //       each candidate until one is found. Store the set of edges that do not reach sourceBlock
    //       (except through the cycle).
    //     * When computing the value of a block whose successors participate in a cycle:
    //       NodeValue=SUM(block.NodeValue / effectiveNumSuccessors for block in successors)
    //       where effectiveNumSuccessors=SUM(1 for s in successors if (b->s) not in cycleOnlyEdges)

    Multiset<BasicBlock> blockCounts = HashMultiset.create();
    int totalPathCount = 0;

    // Should never need to re-visit initial single-track nodes.
    Set<BasicBlock> seen = Sets.newIdentityHashSet();
    seen.addAll(ret);
    ArrayDeque<BasicBlock> pathStack = new ArrayDeque<>();

    pathStack.add(curBlock);
    pathStack.add(curPredecessors.get(0));
    while (true) {
      curBlock = pathStack.getLast();
      curPredecessors = curBlock.getPredecessors();
      if (curBlock == sourceBlock) {
        // Add every block for every connected path.
        blockCounts.addAll(pathStack);
        totalPathCount += 1;
        if (totalPathCount > MAX_PATH_COUNT) {
          if (DEBUG) {
            debugLog(code, "computeSimpleCaseDominatorBlocks: Reached MAX_PATH_COUNT");
          }
          return null;
        }
      } else if (!seen.contains(curBlock)) {
        if (curPredecessors.isEmpty()) {
          // Finding the entry block means the sourceBlock is not a dominator.
          if (DEBUG) {
            debugLog(code, "computeSimpleCaseDominatorBlocks: sourceBlock not a dominator");
          }
          return null;
        }
        // Going deeper.
        BasicBlock nextBlock = findNextUnseen(0, curPredecessors, seen);
        if (nextBlock != null) {
          seen.add(curBlock);
          pathStack.add(nextBlock);
          continue;
        }
      } else {
        seen.remove(curBlock);
      }
      // Popping.
      pathStack.removeLast();
      List<BasicBlock> prevPredecessors = pathStack.getLast().getPredecessors();
      int nextBlockIdx = prevPredecessors.indexOf(curBlock) + 1;
      BasicBlock nextBlock = findNextUnseen(nextBlockIdx, prevPredecessors, seen);
      if (nextBlock != null) {
        pathStack.add(nextBlock);
      } else if (pathStack.size() == 1) {
        break;
      }
    }

    for (var entry : blockCounts.entrySet()) {
      if (entry.getCount() == totalPathCount) {
        ret.add(entry.getElement());
      }
    }
    if (DEBUG) {
      debugLog(code, "computeSimpleCaseDominatorBlocks: PATH COUNT " + totalPathCount);
    }

    return ret;
  }

  /**
   * Attempts to determine all values for the given array. This will work only when:
   *
   * <pre>
   * 1) The Array has a single users (other than array-puts)
   *   * This constraint is to ensure other users do not modify the array.
   *   * When users are in different blocks, their order is hard to know.
   * 2) The array size is a constant.
   * 3) All array-put instructions have constant and unique indices.
   *   * Indices must be unique because order is hard to know when multiple blocks are concerned.
   * 4) The array-put instructions are guaranteed to be executed before singleUser.
   * </pre>
   *
   * @param arrayValue The Value for the array.
   * @param singleUser The only non-array-put user, or null to auto-detect.
   * @return The computed array values, or null if they could not be determined.
   */
  public static ArrayValues computeSingleUseArrayValues(
      Value arrayValue, Instruction singleUser, IRCode code) {
    assert singleUser == null || arrayValue.uniqueUsers().contains(singleUser);
    TypeElement arrayType = arrayValue.getType();
    if (!arrayType.isArrayType() || arrayValue.hasDebugUsers() || arrayValue.isPhi()) {
      return null;
    }

    Instruction definition = arrayValue.definition;
    NewArrayEmpty newArrayEmpty = definition.asNewArrayEmpty();
    NewArrayFilled newArrayFilled = definition.asNewArrayFilled();
    if (newArrayFilled != null) {
      // It would be possible to have new-array-filled followed by aput-array, but that sequence of
      // instructions does not commonly occur, so we don't support it here.
      if (!arrayValue.hasSingleUniqueUser() || arrayValue.hasPhiUsers()) {
        return null;
      }
      return new ArrayValues(newArrayFilled.inValues());
    } else if (newArrayEmpty == null) {
      return null;
    }

    int arraySize = newArrayEmpty.sizeIfConst();
    if (arraySize < 0 || arraySize > MAX_ARRAY_SIZE) {
      // Array is non-const size.
      return null;
    }

    if (singleUser == null) {
      for (Instruction user : arrayValue.uniqueUsers()) {
        ArrayPut arrayPut = user.asArrayPut();
        if (arrayPut == null || arrayPut.array() != arrayValue || arrayPut.value() == arrayValue) {
          if (singleUser == null) {
            singleUser = user;
          } else {
            return null;
          }
        }
      }
    }

    // Ensure that all paths from new-array-empty to |usage| contain all array-put instructions.
    Set<BasicBlock> dominatorBlocks =
        computeSimpleCaseDominatorBlocks(definition.getBlock(), singleUser.getBlock(), code);
    if (dominatorBlocks == null) {
      return null;
    }
    BasicBlock usageBlock = singleUser.getBlock();

    ArrayPut[] arrayPutsByIndex = new ArrayPut[arraySize];
    for (Instruction user : arrayValue.uniqueUsers()) {
      ArrayPut arrayPut = user.asArrayPut();
      if (arrayPut == null || arrayPut.array() != arrayValue || arrayPut.value() == arrayValue) {
        if (user == singleUser) {
          continue;
        }
        // Found a second non-array-put user.
        return null;
      }
      int index = arrayPut.indexIfConstAndInBounds(arraySize);
      if (index < 0) {
        return null;
      }
      if (arrayPut.getBlock() == usageBlock) {
        // Process these later.
        continue;
      } else if (!dominatorBlocks.contains(arrayPut.getBlock())) {
        return null;
      }
      // We do not know what order blocks are in, so do not allow re-assignment.
      if (arrayPutsByIndex[index] != null) {
        return null;
      }
      arrayPutsByIndex[index] = arrayPut;
    }
    boolean seenSingleUser = false;
    for (Instruction inst : usageBlock.getInstructions()) {
      if (inst == singleUser) {
        seenSingleUser = true;
        continue;
      }
      ArrayPut arrayPut = inst.asArrayPut();
      if (arrayPut == null || arrayPut.array() != arrayValue) {
        continue;
      }
      if (seenSingleUser) {
        // Found an array-put after the array was used. This is too uncommon of a thing to support.
        return null;
      }
      int index = arrayPut.index().getConstInstruction().asConstNumber().getIntValue();
      // We can allow reassignment at this point since we are visiting in order.
      arrayPutsByIndex[index] = arrayPut;
    }

    return new ArrayValues(arrayPutsByIndex);
  }
}
