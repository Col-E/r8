// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.framework.intraprocedural;

import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.Instruction;

public interface IRControlFlowGraph extends ControlFlowGraph<BasicBlock, Instruction> {

  @Override
  default boolean hasUniquePredecessor(BasicBlock block) {
    return block.hasUniquePredecessor();
  }

  @Override
  default boolean hasUniqueSuccessor(BasicBlock block) {
    return block.hasUniqueSuccessor();
  }

  @Override
  default boolean hasUniqueSuccessorWithUniquePredecessor(BasicBlock block) {
    return block.hasUniqueSuccessorWithUniquePredecessor();
  }

  @Override
  default BasicBlock getUniqueSuccessor(BasicBlock block) {
    return block.getUniqueSuccessor();
  }
}
