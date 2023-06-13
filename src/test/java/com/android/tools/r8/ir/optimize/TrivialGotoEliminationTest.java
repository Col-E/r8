// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.Argument;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.Goto;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.IRMetadata;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.IfType;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.NumberGenerator;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Position.SyntheticPosition;
import com.android.tools.r8.ir.code.Return;
import com.android.tools.r8.ir.code.Throw;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodConversionOptions.MutableMethodConversionOptions;
import com.android.tools.r8.ir.conversion.passes.TrivialGotosCollapser;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableList;
import java.util.LinkedList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TrivialGotoEliminationTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public TrivialGotoEliminationTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  private final IRMetadata metadata = IRMetadata.unknown();

  @Test
  public void trivialGotoInEntryBlock() throws Exception {
    AppView<AppInfo> appView = computeAppView(AndroidApp.builder().build());
    InternalOptions options = appView.options();
    // Setup silly block structure:
    //
    // block0:
    //   goto block2
    // block1:
    //   v0 = const-number 0
    //   throw v0
    // block2:
    //   return
    final NumberGenerator basicBlockNumberGenerator = new NumberGenerator();
    Position position = SyntheticPosition.builder().setLine(0).disableMethodCheck().build();
    BasicBlock block2 = new BasicBlock();
    BasicBlock block0 =
        BasicBlock.createGotoBlock(basicBlockNumberGenerator.next(), position, metadata, block2);
    BasicBlock block1 = new BasicBlock();
    block1.setNumber(basicBlockNumberGenerator.next());
    block2.setNumber(basicBlockNumberGenerator.next());
    block0.setFilledForTesting();
    block2.getMutablePredecessors().add(block0);
    Instruction ret = new Return();
    ret.setPosition(position);
    block2.add(ret, metadata);
    block2.setFilledForTesting();
    Value value = new Value(0, TypeElement.getInt(), null);
    Instruction number = new ConstNumber(value, 0);
    number.setPosition(position);
    block1.add(number, metadata);
    Instruction throwing = new Throw(value);
    throwing.setPosition(position);
    block1.add(throwing, metadata);
    block1.setFilledForTesting();
    LinkedList<BasicBlock> blocks = new LinkedList<>();
    blocks.add(block0);
    blocks.add(block1);
    blocks.add(block2);
    // Check that the goto in block0 remains. There was a bug in the trivial goto elimination
    // that ended up removing that goto changing the code to start with the unreachable
    // throw.
    options.debug = true;
    IRCode code =
        new IRCode(
            options,
            null,
            Position.none(),
            blocks,
            new NumberGenerator(),
            basicBlockNumberGenerator,
            IRMetadata.unknown(),
            Origin.unknown(),
            new MutableMethodConversionOptions(options));
    new TrivialGotosCollapser(appView).run(code, Timing.empty());
    assertTrue(code.entryBlock().isTrivialGoto());
    assertTrue(blocks.contains(block0));
    assertTrue(blocks.contains(block1));
    assertTrue(blocks.contains(block2));
  }

  @Test
  public void trivialGotoLoopAsFallthrough() throws Exception {
    AppView<AppInfo> appView = computeAppView(AndroidApp.builder().build());
    InternalOptions options = appView.options();
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
    final NumberGenerator basicBlockNumberGenerator = new NumberGenerator();
    Position position = SyntheticPosition.builder().setLine(0).disableMethodCheck().build();
    BasicBlock block0 = new BasicBlock();
    block0.setNumber(basicBlockNumberGenerator.next());
    BasicBlock block2 = new BasicBlock();
    BasicBlock block1 =
        BasicBlock.createGotoBlock(basicBlockNumberGenerator.next(), position, metadata);
    block2.setNumber(basicBlockNumberGenerator.next());
    Instruction ret = new Return();
    ret.setPosition(position);
    block2.add(ret, metadata);
    block2.setFilledForTesting();

    BasicBlock block3 = new BasicBlock();
    block3.setNumber(basicBlockNumberGenerator.next());
    Instruction instruction = new Goto();
    instruction.setPosition(position);
    block3.add(instruction, metadata);
    block3.setFilledForTesting();
    block3.getMutableSuccessors().add(block3);

    block1.getMutableSuccessors().add(block3);
    block1.setFilledForTesting();

    Value value =
        new Value(
            0,
            TypeElement.fromDexType(
                options.itemFactory.throwableType, Nullability.definitelyNotNull(), appView),
            null);
    instruction = new Argument(value, 0, false);
    instruction.setPosition(position);
    block0.add(instruction, metadata);
    instruction = new If(IfType.EQ, value);
    instruction.setPosition(position);
    block0.add(instruction, metadata);
    block0.getMutableSuccessors().add(block2);
    block0.getMutableSuccessors().add(block1);
    block0.setFilledForTesting();

    block1.getMutablePredecessors().add(block0);
    block2.getMutablePredecessors().add(block0);
    block3.getMutablePredecessors().add(block1);
    block3.getMutablePredecessors().add(block3);

    LinkedList<BasicBlock> blocks = new LinkedList<>();
    blocks.add(block0);
    blocks.add(block1);
    blocks.add(block2);
    blocks.add(block3);
    // Check that the goto in block0 remains. There was a bug in the trivial goto elimination
    // that ended up removing that goto changing the code to start with the unreachable
    // throw.
    options.debug = true;
    IRCode code =
        new IRCode(
            options,
            null,
            Position.none(),
            blocks,
            new NumberGenerator(),
            basicBlockNumberGenerator,
            IRMetadata.unknown(),
            Origin.unknown(),
            new MutableMethodConversionOptions(options));
    new TrivialGotosCollapser(appView).run(code, Timing.empty());
    assertTrue(block0.getInstructions().get(1).isIf());
    assertEquals(block1, block0.getInstructions().get(1).asIf().fallthroughBlock());
    assertTrue(blocks.containsAll(ImmutableList.of(block0, block1, block2, block3)));
  }
}
