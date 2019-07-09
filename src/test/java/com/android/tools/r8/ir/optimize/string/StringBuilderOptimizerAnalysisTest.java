// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.string;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.AnalysisTestBase;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeMethod;
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
  public void testTrivialSequence() throws Exception {
    buildAndCheckIR("trivialSequence", checkOptimizerStates(optimizer -> {
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
  public void testNonStringArgs() throws Exception {
    buildAndCheckIR("nonStringArgs", checkOptimizerStates(optimizer -> {
      // TODO(b/114002137): Improve arg extraction and type conversion.
      assertEquals(0, optimizer.analysis.builderStates.size());
      assertEquals(0, optimizer.analysis.simplifiedBuilders.size());
    }));
  }

  @Test
  public void testTypeConversion() throws Exception {
    buildAndCheckIR("typeConversion", checkOptimizerStates(optimizer -> {
      // TODO(b/114002137): Improve arg extraction and type conversion.
      assertEquals(0, optimizer.analysis.builderStates.size());
      assertEquals(0, optimizer.analysis.simplifiedBuilders.size());
    }));
  }

  @Test
  public void testNestedBuilders_appendBuilderItself() throws Exception {
    buildAndCheckIR("nestedBuilders_appendBuilderItself", checkOptimizerStates(optimizer -> {
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
    buildAndCheckIR("nestedBuilders_appendBuilderResult", checkOptimizerStates(optimizer -> {
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
    buildAndCheckIR("simplePhi", checkOptimizerStates(optimizer -> {
      // TODO(b/114002137): Improve arg extraction and type conversion.
      assertEquals(0, optimizer.analysis.simplifiedBuilders.size());
    }));
  }

  // TODO(b/114002137): Make sure analysis result / tests don't depend on VMs.
  @Ignore("b/114002137")
  @Test
  public void testPhiAtInit() throws Exception {
    buildAndCheckIR("phiAtInit", checkOptimizerStates(optimizer -> {
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
  public void testPhiWithDifferentInits() throws Exception {
    buildAndCheckIR("phiWithDifferentInits", checkOptimizerStates(optimizer -> {
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
  public void testLoop() throws Exception {
    buildAndCheckIR("loop", checkOptimizerStates(optimizer -> {
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
    buildAndCheckIR("loopWithBuilder", checkOptimizerStates(optimizer -> {
      assertEquals(1, optimizer.analysis.builderStates.size());
      for (Value builder : optimizer.analysis.builderStates.keySet()) {
        Map<Instruction, BuilderState> perBuilderState =
            optimizer.analysis.builderStates.get(builder);
        checkBuilderState(optimizer, perBuilderState, null, true);
      }
      assertEquals(0, optimizer.analysis.simplifiedBuilders.size());
    }));
  }

  // TODO(b/114002137): later, test what const-string is computed for builders.
  private Consumer<IRCode> checkOptimizerStates(
      Consumer<StringBuilderOptimizer> optimizerConsumer) {
    return code -> {
      StringBuilderOptimizer optimizer =
          new StringBuilderOptimizer(
              appView, new StringBuilderOptimizationConfigurationForTesting());
      optimizer.computeTrivialStringConcatenation(code);
      optimizerConsumer.accept(optimizer);
    };
  }

  private void checkBuilderState(
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

  class StringBuilderOptimizationConfigurationForTesting
      implements StringBuilderOptimizationConfiguration {
    @Override
    public boolean isBuilderType(DexType type) {
      String descriptor = type.toDescriptorString();
      return descriptor.equals(appView.dexItemFactory().stringBuilderType.toDescriptorString())
          || descriptor.equals(appView.dexItemFactory().stringBufferType.toDescriptorString());
    }

    @Override
    public boolean isBuilderInit(DexMethod method, DexType builderType) {
      return builderType == method.holder
          && method.name.toString().equals("<init>");
    }

    @Override
    public boolean isBuilderInit(DexMethod method) {
      return isBuilderType(method.holder)
          && method.name.toString().equals("<init>");
    }

    @Override
    public boolean isAppendMethod(DexMethod method) {
      return isBuilderType(method.holder) && method.name.toString().equals("append");
    }

    @Override
    public boolean isSupportedAppendMethod(InvokeMethod invoke) {
      DexMethod invokedMethod = invoke.getInvokedMethod();
      assert isAppendMethod(invokedMethod);
      if (invoke.inValues().size() > 2) {
        return false;
      }
      for (DexType argType : invokedMethod.proto.parameters.values) {
        if (!canHandleArgumentType(argType)) {
          return false;
        }
      }
      return true;
    }

    @Override
    public boolean isToStringMethod(DexMethod method) {
      return isBuilderType(method.holder) && method.name.toString().equals("toString");
    }

    private boolean canHandleArgumentType(DexType argType) {
      String descriptor = argType.toDescriptorString();
      return descriptor.equals(appView.dexItemFactory().stringType.toDescriptorString())
          || descriptor.equals(appView.dexItemFactory().charSequenceType.toDescriptorString());
    }
  }
}
