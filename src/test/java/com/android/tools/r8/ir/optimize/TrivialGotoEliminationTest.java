// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Return;
import com.android.tools.r8.ir.code.Throw;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.code.ValueNumberGenerator;
import com.android.tools.r8.ir.code.ValueType;
import com.google.common.collect.ImmutableList;
import java.util.LinkedList;
import org.junit.Test;

public class TrivialGotoEliminationTest {
  @Test
  public void trivialGotoInEntryBlock() {
    // Setup silly block structure:
    //
    // block0:
    //   goto block2
    // block1:
    //   v0 = const-number 0
    //   throw v0
    // block2:
    //   return
    BasicBlock block2 = new BasicBlock();
    block2.setNumber(2);
    BasicBlock block0 = BasicBlock.createGotoBlock(0, block2);
    block0.setFilledForTesting();
    block2.getPredecessors().add(block0);
    Instruction ret = new Return();
    ret.setPosition(Position.none());
    block2.add(ret);
    block2.setFilledForTesting();
    BasicBlock block1 = new BasicBlock();
    block1.setNumber(1);
    Value value = new Value(0, ValueType.INT, null);
    Instruction number = new ConstNumber(value, 0);
    number.setPosition(Position.none());
    block1.add(number);
    Instruction throwing = new Throw(value);
    throwing.setPosition(Position.none());
    block1.add(throwing);
    block1.setFilledForTesting();
    LinkedList<BasicBlock> blocks = new LinkedList<>();
    blocks.add(block0);
    blocks.add(block1);
    blocks.add(block2);
    // Check that the goto in block0 remains. There was a bug in the trivial goto elimination
    // that ended up removing that goto changing the code to start with the unreachable
    // throw.
    IRCode code = new IRCode(null, blocks, new ValueNumberGenerator(), false);
    CodeRewriter.collapsTrivialGotos(null, code);
    assert code.blocks.get(0).isTrivialGoto();
    assert blocks.contains(block0);
    assert blocks.contains(block1);
    assert blocks.contains(block2);
  }
}
