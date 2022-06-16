// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.framework.intraprocedural.cf;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.StringUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** A basic block for {@link com.android.tools.r8.graph.CfCode}. */
public class CfBlock {

  // The CfCode instruction index of the block's first instruction.
  int firstInstructionIndex = -1;

  // The CfCode instruction index of the block's first throwing instruction.
  int firstThrowingInstructionIndex = -1;

  // The CfCode instruction index of the block's last instruction.
  int lastInstructionIndex = -1;

  // The predecessors of the block. These are stored explicitly (unlike the successors) since they
  // cannot efficiently be computed from the block.
  final Set<CfBlock> predecessors = new LinkedHashSet<>();

  // The exceptional predecessors of the block.
  final List<CfBlock> exceptionalPredecessors = new ArrayList<>();

  // The exceptional successors of the block (i.e., the catch handlers of the block).
  final LinkedHashMap<DexType, CfBlock> exceptionalSuccessors = new LinkedHashMap<>();

  public CfInstruction getFallthroughInstruction(CfCode code) {
    int fallthroughInstructionIndex = getLastInstructionIndex() + 1;
    return fallthroughInstructionIndex < code.getInstructions().size()
        ? code.getInstructions().get(fallthroughInstructionIndex)
        : null;
  }

  public int getFirstInstructionIndex() {
    return firstInstructionIndex;
  }

  public boolean hasThrowingInstruction() {
    return firstThrowingInstructionIndex >= 0;
  }

  public int getFirstThrowingInstructionIndex() {
    return firstThrowingInstructionIndex;
  }

  public CfInstruction getLastInstruction(CfCode code) {
    return code.getInstructions().get(lastInstructionIndex);
  }

  public int getLastInstructionIndex() {
    return lastInstructionIndex;
  }

  public Collection<CfBlock> getPredecessors() {
    return predecessors;
  }

  public List<CfBlock> getExceptionalPredecessors() {
    return exceptionalPredecessors;
  }

  public LinkedHashMap<DexType, CfBlock> getExceptionalSuccessors() {
    return exceptionalSuccessors;
  }

  @Override
  public String toString() {
    List<String> predecessorStrings = new ArrayList<>();
    predecessors.forEach(p -> predecessorStrings.add(p.getRangeString()));
    exceptionalPredecessors.forEach(p -> predecessorStrings.add("*" + p.getRangeString()));
    return "CfBlock(range="
        + getRangeString()
        + ", predecessors="
        + StringUtils.join(", ", predecessorStrings)
        + ")";
  }

  private String getRangeString() {
    return firstInstructionIndex + "->" + lastInstructionIndex;
  }

  // A mutable interface for block construction.
  static class MutableCfBlock extends CfBlock {

    void addPredecessor(CfBlock block) {
      predecessors.add(block);
    }

    void addExceptionalPredecessor(CfBlock block) {
      exceptionalPredecessors.add(block);
    }

    void addExceptionalSuccessor(CfBlock block, DexType guard) {
      assert !exceptionalSuccessors.containsKey(guard);
      exceptionalSuccessors.put(guard, block);
    }

    void setFirstInstructionIndex(int firstInstructionIndex) {
      this.firstInstructionIndex = firstInstructionIndex;
    }

    void setFirstThrowingInstructionIndex(int firstThrowingInstructionIndex) {
      this.firstThrowingInstructionIndex = firstThrowingInstructionIndex;
    }

    void setLastInstructionIndex(int lastInstructionIndex) {
      this.lastInstructionIndex = lastInstructionIndex;
    }

    boolean validate(CfControlFlowGraph cfg, InternalOptions options) {
      assert 0 <= firstInstructionIndex;
      assert firstInstructionIndex <= lastInstructionIndex;
      assert firstThrowingInstructionIndex < 0
          || firstInstructionIndex <= firstThrowingInstructionIndex;
      assert firstThrowingInstructionIndex < 0
          || firstThrowingInstructionIndex <= lastInstructionIndex;
      assert SetUtils.newIdentityHashSet(predecessors).size() == predecessors.size();
      assert this == cfg.getEntryBlock()
          || !predecessors.isEmpty()
          || !exceptionalPredecessors.isEmpty()
          || options.getCfCodeAnalysisOptions().isUnreachableCfBlocksAllowed();
      return true;
    }
  }
}
