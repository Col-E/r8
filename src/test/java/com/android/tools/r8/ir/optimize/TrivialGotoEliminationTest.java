// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.code.Argument;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.Goto;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.If.Type;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Return;
import com.android.tools.r8.ir.code.Throw;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.code.ValueNumberGenerator;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;
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
    Position position = Position.testingPosition();
    BasicBlock block2 = new BasicBlock();
    block2.setNumber(2);
    BasicBlock block0 = BasicBlock.createGotoBlock(0, position, block2);
    block0.setFilledForTesting();
    block2.getPredecessors().add(block0);
    Instruction ret = new Return();
    ret.setPosition(position);
    block2.add(ret);
    block2.setFilledForTesting();
    BasicBlock block1 = new BasicBlock();
    block1.setNumber(1);
    Value value = new Value(0, TypeLatticeElement.INT, null);
    Instruction number = new ConstNumber(value, 0);
    number.setPosition(position);
    block1.add(number);
    Instruction throwing = new Throw(value);
    throwing.setPosition(position);
    block1.add(throwing);
    block1.setFilledForTesting();
    LinkedList<BasicBlock> blocks = new LinkedList<>();
    blocks.add(block0);
    blocks.add(block1);
    blocks.add(block2);
    // Check that the goto in block0 remains. There was a bug in the trivial goto elimination
    // that ended up removing that goto changing the code to start with the unreachable
    // throw.
    IRCode code =
        new IRCode(
            new InternalOptions(),
            null,
            blocks,
            new ValueNumberGenerator(),
            false,
            false,
            Origin.unknown());
    CodeRewriter.collapseTrivialGotos(null, code);
    assertTrue(code.blocks.get(0).isTrivialGoto());
    assertTrue(blocks.contains(block0));
    assertTrue(blocks.contains(block1));
    assertTrue(blocks.contains(block2));
  }

  @Test
  public void trivialGotoLoopAsFallthrough() {
    DexApplication app = DexApplication.builder(new DexItemFactory(), new Timing("")).build();
    AppInfo appInfo = new AppInfo(app);
    // Setup block structure:
    // block0:
    //   v0 <- argument
    //   if ne v0 block2
    //
    // block1:
    //   goto block3
    //
    // block2:
    //   return
    //
    // block3:
    //   goto block3
    Position position = Position.testingPosition();
    BasicBlock block2 = new BasicBlock();
    block2.setNumber(2);
    Instruction ret = new Return();
    ret.setPosition(position);
    block2.add(ret);
    block2.setFilledForTesting();

    BasicBlock block3 = new BasicBlock();
    block3.setNumber(3);
    Instruction instruction = new Goto();
    instruction.setPosition(position);
    block3.add(instruction);
    block3.setFilledForTesting();
    block3.getSuccessors().add(block3);

    BasicBlock block1 = BasicBlock.createGotoBlock(1, position);
    block1.getSuccessors().add(block3);
    block1.setFilledForTesting();

    BasicBlock block0 = new BasicBlock();
    block0.setNumber(0);
    Value value =
        new Value(
            0, TypeLatticeElement.fromDexType(DexItemFactory.catchAllType, false, appInfo), null);
    instruction = new Argument(value);
    instruction.setPosition(position);
    block0.add(instruction);
    instruction = new If(Type.EQ, value);
    instruction.setPosition(position);
    block0.add(instruction);
    block0.getSuccessors().add(block2);
    block0.getSuccessors().add(block1);
    block0.setFilledForTesting();

    block1.getPredecessors().add(block0);
    block2.getPredecessors().add(block0);
    block3.getPredecessors().add(block1);
    block3.getPredecessors().add(block3);

    LinkedList<BasicBlock> blocks = new LinkedList<>();
    blocks.add(block0);
    blocks.add(block1);
    blocks.add(block2);
    blocks.add(block3);
    // Check that the goto in block0 remains. There was a bug in the trivial goto elimination
    // that ended up removing that goto changing the code to start with the unreachable
    // throw.
    IRCode code =
        new IRCode(
            new InternalOptions(),
            null,
            blocks,
            new ValueNumberGenerator(),
            false,
            false,
            Origin.unknown());
    CodeRewriter.collapseTrivialGotos(null, code);
    assertTrue(block0.getInstructions().get(1).isIf());
    assertEquals(block1, block0.getInstructions().get(1).asIf().fallthroughBlock());
    assertTrue(blocks.containsAll(ImmutableList.of(block0, block1, block2, block3)));
  }
}
