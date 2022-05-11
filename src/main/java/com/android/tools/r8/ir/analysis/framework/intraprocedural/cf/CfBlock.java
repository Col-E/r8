// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.framework.intraprocedural.cf;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.utils.SetUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** A basic block for {@link com.android.tools.r8.graph.CfCode}. */
public class CfBlock {

  // The CfCode instruction index of the block's first instruction.
  int firstInstructionIndex = -1;

  // The CfCode instruction index of the block's last instruction.
  int lastInstructionIndex = -1;

  // The predecessors of the block. These are stored explicitly (unlike the successors) since they
  // cannot efficiently be computed from the block.
  final Set<CfBlock> predecessors = new LinkedHashSet<>();

  // The exceptional predecessors of the block.
  final List<CfBlock> exceptionalPredecessors = new ArrayList<>();

  // The exceptional successors of the block (i.e., the catch handlers of the block).
  final List<CfBlock> exceptionalSuccessors = new ArrayList<>();

  public CfInstruction getFallthroughInstruction(CfCode code) {
    int fallthroughInstructionIndex = getLastInstructionIndex() + 1;
    return fallthroughInstructionIndex < code.getInstructions().size()
        ? code.getInstructions().get(fallthroughInstructionIndex)
        : null;
  }

  public int getFirstInstructionIndex() {
    return firstInstructionIndex;
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

  // TODO(b/214496607): This currently only encodes the graph, but we likely need to include the
  //  guard types here.
  public List<CfBlock> getExceptionalPredecessors() {
    return exceptionalPredecessors;
  }

  // TODO(b/214496607): This currently only encodes the graph, but we likely need to include the
  //  guard types here.
  public List<CfBlock> getExceptionalSuccessors() {
    return exceptionalSuccessors;
  }

  // A mutable interface for block construction.
  static class MutableCfBlock extends CfBlock {

    void addPredecessor(CfBlock block) {
      predecessors.add(block);
    }

    void addExceptionalPredecessor(CfBlock block) {
      exceptionalPredecessors.add(block);
    }

    void addExceptionalSuccessor(CfBlock block) {
      exceptionalSuccessors.add(block);
    }

    void setFirstInstructionIndex(int firstInstructionIndex) {
      this.firstInstructionIndex = firstInstructionIndex;
    }

    void setLastInstructionIndex(int lastInstructionIndex) {
      this.lastInstructionIndex = lastInstructionIndex;
    }

    boolean validate() {
      assert 0 <= firstInstructionIndex;
      assert firstInstructionIndex <= lastInstructionIndex;
      assert SetUtils.newIdentityHashSet(predecessors).size() == predecessors.size();
      return true;
    }
  }
}
