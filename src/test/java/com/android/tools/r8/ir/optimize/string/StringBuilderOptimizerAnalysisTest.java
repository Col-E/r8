// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.string;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.analysis.AnalysisTestBase;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.string.StringBuilderOptimizer.BuilderState;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class StringBuilderOptimizerAnalysisTest extends AnalysisTestBase {

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().build();
  }

  public StringBuilderOptimizerAnalysisTest(TestParameters parameters) throws Exception {
    super(
        parameters,
        StringConcatenationTestClass.class.getTypeName(),
        StringConcatenationTestClass.class);
  }

  @Test
  public void testUnusedBuilder() throws Exception {
    buildAndCheckIR(
        "unusedBuilder",
        checkOptimizerStates(appView, optimizer -> {
          assertEquals(1, optimizer.analysis.builderStates.size());
          for (Value builder : optimizer.analysis.builderStates.keySet()) {
            Map<Instruction, BuilderState> perBuilderState =
                optimizer.analysis.builderStates.get(builder);
            checkBuilderState(optimizer, perBuilderState, "42", true);
          }
          assertEquals(1, optimizer.analysis.deadBuilders.size());
          assertEquals(0, optimizer.analysis.simplifiedBuilders.size());
        }));
  }

  @Test
  public void testTrivialSequence() throws Exception {
    buildAndCheckIR(
        "trivialSequence",
        checkOptimizerStates(appView, optimizer -> {
          assertEquals(1, optimizer.analysis.builderStates.size());
          for (Value builder : optimizer.analysis.builderStates.keySet()) {
            Map<Instruction, BuilderState> perBuilderState =
                optimizer.analysis.builderStates.get(builder);
            checkBuilderState(optimizer, perBuilderState, "xyz", true);
          }
          assertEquals(1, optimizer.analysis.simplifiedBuilders.size());
        }));
  }

  @Test
  public void testBuilderWithInitialValue() throws Exception {
    buildAndCheckIR(
        "builderWithInitialValue",
        checkOptimizerStates(appView, optimizer -> {
          assertEquals(1, optimizer.analysis.builderStates.size());
          for (Value builder : optimizer.analysis.builderStates.keySet()) {
            Map<Instruction, BuilderState> perBuilderState =
                optimizer.analysis.builderStates.get(builder);
            checkBuilderState(optimizer, perBuilderState, "Hello,R8", true);
          }
          assertEquals(1, optimizer.analysis.simplifiedBuilders.size());
        }));
  }

  @Test
  public void testBuilderWithCapacity() throws Exception {
    buildAndCheckIR(
        "builderWithCapacity",
        checkOptimizerStates(appView, optimizer -> {
          assertEquals(1, optimizer.analysis.builderStates.size());
          for (Value builder : optimizer.analysis.builderStates.keySet()) {
            Map<Instruction, BuilderState> perBuilderState =
                optimizer.analysis.builderStates.get(builder);
            checkBuilderState(optimizer, perBuilderState, "42", true);
          }
          assertEquals(1, optimizer.analysis.simplifiedBuilders.size());
        }));
  }

  @Test
  public void testNonStringArgs() throws Exception {
    buildAndCheckIR(
        "nonStringArgs",
        checkOptimizerStates(appView, optimizer -> {
          assertEquals(1, optimizer.analysis.builderStates.size());
          for (Value builder : optimizer.analysis.builderStates.keySet()) {
            Map<Instruction, BuilderState> perBuilderState =
                optimizer.analysis.builderStates.get(builder);
            checkBuilderState(optimizer, perBuilderState, "42", true);
          }
          assertEquals(1, optimizer.analysis.simplifiedBuilders.size());
        }));
  }

  @Test
  public void testTypeConversion() throws Exception {
    buildAndCheckIR(
        "typeConversion",
        checkOptimizerStates(appView, optimizer -> {
          assertEquals(1, optimizer.analysis.builderStates.size());
          for (Value builder : optimizer.analysis.builderStates.keySet()) {
            Map<Instruction, BuilderState> perBuilderState =
                optimizer.analysis.builderStates.get(builder);
            checkBuilderState(optimizer, perBuilderState, "0.14 0 false null", true);
          }
          assertEquals(1, optimizer.analysis.simplifiedBuilders.size());
        }));
  }

  @Test
  public void testTypeConversion_withPhis() throws Exception {
    buildAndCheckIR(
        "typeConversion_withPhis",
        checkOptimizerStates(appView, optimizer -> {
          assertEquals(1, optimizer.analysis.builderStates.size());
          for (Value builder : optimizer.analysis.builderStates.keySet()) {
            Map<Instruction, BuilderState> perBuilderState =
                optimizer.analysis.builderStates.get(builder);
            checkBuilderState(optimizer, perBuilderState, null, true);
          }
          assertEquals(0, optimizer.analysis.simplifiedBuilders.size());
        }));
  }

  @Ignore("TODO(b/113859361): passed to another builder should be an eligible case.")
  @Test
  public void testNestedBuilders_appendBuilderItself() throws Exception {
    buildAndCheckIR(
        "nestedBuilders_appendBuilderItself",
        checkOptimizerStates(appView, optimizer -> {
          assertEquals(1, optimizer.analysis.builderStates.size());
          for (Value builder : optimizer.analysis.builderStates.keySet()) {
            Map<Instruction, BuilderState> perBuilderState =
                optimizer.analysis.builderStates.get(builder);
            checkBuilderState(optimizer, perBuilderState, null, true);
          }
          assertEquals(0, optimizer.analysis.simplifiedBuilders.size());
        }));
  }

  @Test
  public void testNestedBuilders_appendBuilderResult() throws Exception {
    buildAndCheckIR(
        "nestedBuilders_appendBuilderResult",
        checkOptimizerStates(appView, optimizer -> {
          assertEquals(2, optimizer.analysis.builderStates.size());
          for (Value builder : optimizer.analysis.builderStates.keySet()) {
            Map<Instruction, BuilderState> perBuilderState =
                optimizer.analysis.builderStates.get(builder);
            String expectedResult =
                optimizer.analysis.simplifiedBuilders.contains(builder) ? "R8" : null;
            checkBuilderState(optimizer, perBuilderState, expectedResult, true);
          }
          assertEquals(1, optimizer.analysis.simplifiedBuilders.size());
        }));
  }

  @Test
  public void testSimplePhi() throws Exception {
    buildAndCheckIR(
        "simplePhi",
        checkOptimizerStates(appView, optimizer -> {
          assertEquals(0, optimizer.analysis.builderStates.size());
          assertEquals(0, optimizer.analysis.simplifiedBuilders.size());
        }));
  }

  @Test
  public void testPhiAtInit() throws Exception {
    int expectedNumOfNewBuilder = 2;
    boolean expectToMeetToString = false;
    if (parameters.isDexRuntime()
        && parameters.getRuntime().asDex().getVm().isOlderThanOrEqual(DexVm.ART_5_1_1_HOST)) {
      expectedNumOfNewBuilder = 1;
      expectToMeetToString = true;
    }
    final int finalExpectedNumOfNewBuilder = expectedNumOfNewBuilder;
    final boolean finalExpectToMeetToString = expectToMeetToString;
    buildAndCheckIR(
        "phiAtInit",
        checkOptimizerStates(appView, optimizer -> {
          assertEquals(finalExpectedNumOfNewBuilder, optimizer.analysis.builderStates.size());
          for (Value builder : optimizer.analysis.builderStates.keySet()) {
            Map<Instruction, BuilderState> perBuilderState =
                optimizer.analysis.builderStates.get(builder);
            checkBuilderState(optimizer, perBuilderState, null, finalExpectToMeetToString);
          }
          assertEquals(0, optimizer.analysis.simplifiedBuilders.size());
        }));
  }

  @Test
  public void testPhiWithDifferentInits() throws Exception {
    buildAndCheckIR(
        "phiWithDifferentInits",
        checkOptimizerStates(appView, optimizer -> {
          assertEquals(2, optimizer.analysis.builderStates.size());
          for (Value builder : optimizer.analysis.builderStates.keySet()) {
            Map<Instruction, BuilderState> perBuilderState =
                optimizer.analysis.builderStates.get(builder);
            checkBuilderState(optimizer, perBuilderState, null, false);
          }
          assertEquals(0, optimizer.analysis.simplifiedBuilders.size());
        }));
  }

  @Test
  public void testConditionalPhiWithoutAppend() throws Exception {
    buildAndCheckIR(
        "conditionalPhiWithoutAppend",
        checkOptimizerStates(appView, optimizer -> {
          assertEquals(1, optimizer.analysis.builderStates.size());
          for (Value builder : optimizer.analysis.builderStates.keySet()) {
            Map<Instruction, BuilderState> perBuilderState =
                optimizer.analysis.builderStates.get(builder);
            checkBuilderState(optimizer, perBuilderState, null, true);
          }
          assertEquals(0, optimizer.analysis.simplifiedBuilders.size());
        }));
  }

  @Test
  public void testLoop() throws Exception {
    buildAndCheckIR(
        "loop",
        checkOptimizerStates(appView, optimizer -> {
          assertEquals(2, optimizer.analysis.builderStates.size());
          for (Value builder : optimizer.analysis.builderStates.keySet()) {
            Map<Instruction, BuilderState> perBuilderState =
                optimizer.analysis.builderStates.get(builder);
            checkBuilderState(optimizer, perBuilderState, null, true);
          }
          assertEquals(0, optimizer.analysis.simplifiedBuilders.size());
        }));
  }

  @Test
  public void testLoopWithBuilder() throws Exception {
    buildAndCheckIR(
        "loopWithBuilder",
        checkOptimizerStates(appView, optimizer -> {
          assertEquals(1, optimizer.analysis.builderStates.size());
          for (Value builder : optimizer.analysis.builderStates.keySet()) {
            Map<Instruction, BuilderState> perBuilderState =
                optimizer.analysis.builderStates.get(builder);
            checkBuilderState(optimizer, perBuilderState, null, true);
          }
          assertEquals(0, optimizer.analysis.simplifiedBuilders.size());
        }));
  }

  static Consumer<IRCode> checkOptimizerStates(
      AppView<?> appView, Consumer<StringBuilderOptimizer> optimizerConsumer) {
    return code -> {
      StringBuilderOptimizer optimizer = new StringBuilderOptimizer(appView);
      optimizer.computeTrivialStringConcatenation(code);
      optimizerConsumer.accept(optimizer);
    };
  }

  static void checkBuilderState(
      StringBuilderOptimizer optimizer,
      Map<Instruction, BuilderState> perBuilderState,
      String expectedConstString,
      boolean expectToSeeToString) {
    boolean metToString = false;
    for (Map.Entry<Instruction, BuilderState> entry : perBuilderState.entrySet()) {
      if (entry.getKey().isInvokeMethod()
        && optimizer.optimizationConfiguration.isToStringMethod(
            entry.getKey().asInvokeMethod().getInvokedMethod())) {
        metToString = true;
        assertEquals(expectedConstString, optimizer.analysis.toCompileTimeString(entry.getValue()));
      }
    }
    assertEquals(expectToSeeToString, metToString);
  }
}
