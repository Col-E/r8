// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.Div;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.IRMetadata;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.Move;
import com.android.tools.r8.ir.code.NumberGenerator;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Position.SyntheticPosition;
import com.android.tools.r8.ir.code.Return;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
import com.android.tools.r8.ir.regalloc.LinearScanRegisterAllocator;
import com.android.tools.r8.ir.regalloc.LiveIntervals;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.synthesis.SyntheticItems.GlobalSyntheticsStrategy;
import com.android.tools.r8.utils.InternalOptions;
import java.util.LinkedList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ConstantRemovalTest {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return TestParameters.builder().withNoneRuntime().build();
  }

  public ConstantRemovalTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  private static class MockLinearScanRegisterAllocator extends LinearScanRegisterAllocator {
    MockLinearScanRegisterAllocator(AppView<?> appView, IRCode code) {
      super(appView, code);
    }

    @Override
    public int getRegisterForValue(Value value, int instructionNumber) {
      return value.getNumber();
    }
  }

  private static class MockLiveIntervals extends LiveIntervals {
    MockLiveIntervals(Value value) {
      super(value);
    }

    @Override
    public LiveIntervals getSplitCovering(int i) {
      return this;
    }
  }

  @Test
  public void removeConstantsTest() {
    // Produce a basic block representing the code:
    //
    // ConstNumber          v3 <-  0 (LONG)
    // ConstNumber          v0(10) <-  10 (LONG)
    // Div                  v3 <- v3, v0(10)
    // ConstNumber          v2(10) <-  10 (INT)
    // Move                 v1 <- v2(10) (INT)
    // Div                  v1 <- v1, v1
    // ConstNumber          v0(10) <-  10 (LONG)
    // Div                  v3 <- v3, v0(10)
    // Return
    //
    // Use a register allocator that uses the value number as the register.
    //
    // Then test that peephole optimization realizes that the last const number
    // is needed and the value 10 is *not* still in register 0 at that point.
    final NumberGenerator basicBlockNumberGenerator = new NumberGenerator();
    BasicBlock block = new BasicBlock();
    block.setNumber(basicBlockNumberGenerator.next());

    IRMetadata metadata = IRMetadata.unknown();
    Position position = SyntheticPosition.builder().disableMethodCheck().setLine(0).build();

    Value v3 = new Value(3, TypeElement.getLong(), null);
    v3.setNeedsRegister(true);
    new MockLiveIntervals(v3);
    Instruction instruction = new ConstNumber(v3, 0);
    instruction.setPosition(position);
    block.add(instruction, metadata);

    Value v0 = new Value(0, TypeElement.getLong(), null);
    v0.setNeedsRegister(true);
    new MockLiveIntervals(v0);
    instruction = new ConstNumber(v0, 10);
    instruction.setPosition(position);
    block.add(instruction, metadata);

    instruction = new Div(NumericType.LONG, v3, v3, v0);
    instruction.setPosition(position);
    block.add(instruction, metadata);

    Value v2 = new Value(2, TypeElement.getInt(), null);
    v2.setNeedsRegister(true);
    new MockLiveIntervals(v2);
    instruction = new ConstNumber(v2, 10);
    instruction.setPosition(position);
    block.add(instruction, metadata);

    Value v1 = new Value(1, TypeElement.getInt(), null);
    v1.setNeedsRegister(true);
    new MockLiveIntervals(v1);
    instruction = new Move(v1 ,v2);
    instruction.setPosition(position);
    block.add(instruction, metadata);

    instruction = new Div(NumericType.INT, v1, v1, v1);
    instruction.setPosition(position);
    block.add(instruction, metadata);

    Value v0_2 = new Value(0, TypeElement.getLong(), null);
    v0_2.setNeedsRegister(true);
    new MockLiveIntervals(v0_2);
    instruction = new ConstNumber(v0_2, 10);
    instruction.setPosition(position);
    block.add(instruction, metadata);

    instruction = new Div(NumericType.LONG, v3, v3, v0_2);
    instruction.setPosition(position);
    block.add(instruction, metadata);

    Instruction ret = new Return();
    ret.setPosition(position);
    block.add(ret, metadata);
    block.setFilledForTesting();

    LinkedList<BasicBlock> blocks = new LinkedList<>();
    blocks.add(block);

    InternalOptions options = new InternalOptions();
    options.debug = true;
    AppInfo appInfo =
        AppInfo.createInitialAppInfo(
            DexApplication.builder(options, null).build(),
            GlobalSyntheticsStrategy.forNonSynthesizing());
    AppView<?> appView = AppView.createForD8(appInfo);
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
            MethodConversionOptions.nonConverting());
    PeepholeOptimizer.optimize(appView, code, new MockLinearScanRegisterAllocator(appView, code));

    // Check that all four constant number instructions remain.
    assertEquals(
        4,
        code.entryBlock().getInstructions().stream().filter(Instruction::isConstNumber).count());
  }
}
