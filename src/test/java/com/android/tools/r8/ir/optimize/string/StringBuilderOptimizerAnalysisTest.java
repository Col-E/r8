// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.string;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.AnalysisTestBase;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Value;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.Ignore;
import org.junit.Test;

public class StringBuilderOptimizerAnalysisTest extends AnalysisTestBase {

  public StringBuilderOptimizerAnalysisTest() throws Exception {
    super(StringConcatenationTestClass.class.getTypeName(), StringConcatenationTestClass.class);
  }

  @Test
  public void testTrivialSequence() throws Exception {
    buildAndCheckIR("trivialSequence", checkBuilderDetection(builders -> {
      assertEquals(1, builders.size());
    }));
  }

  @Test
  public void testNonStringArgs() throws Exception {
    buildAndCheckIR("nonStringArgs", checkBuilderDetection(builders -> {
      // TODO(b/114002137): Improve arg extraction and type conversion.
      assertEquals(0, builders.size());
    }));
  }

  @Test
  public void testTypeConversion() throws Exception {
    buildAndCheckIR("typeConversion", checkBuilderDetection(builders -> {
      // TODO(b/114002137): Improve arg extraction and type conversion.
      assertEquals(0, builders.size());
    }));
  }

  @Test
  public void testNestedBuilders_appendBuilderItself() throws Exception {
    buildAndCheckIR("nestedBuilders_appendBuilderItself", checkBuilderDetection(builders -> {
      assertEquals(1, builders.size());
    }));
  }

  @Test
  public void testNestedBuilders_appendBuilderResult() throws Exception {
    buildAndCheckIR("nestedBuilders_appendBuilderResult", checkBuilderDetection(builders -> {
      assertEquals(2, builders.size());
    }));
  }

  @Test
  public void testSimplePhi() throws Exception {
    buildAndCheckIR("simplePhi", checkBuilderDetection(builders -> {
      // TODO(b/114002137): Improve arg extraction and type conversion.
      assertEquals(0, builders.size());
    }));
  }

  // TODO(b/114002137): Parameterize tests and make sure analysis result is same at all VMs.
  @Ignore("b/114002137")
  @Test
  public void testPhiAtInit() throws Exception {
    buildAndCheckIR("phiAtInit", checkBuilderDetection(builders -> {
      assertEquals(2, builders.size());
    }));
  }

  @Test
  public void testPhiWithDifferentInits() throws Exception {
    buildAndCheckIR("phiWithDifferentInits", checkBuilderDetection(builders -> {
      assertEquals(2, builders.size());
    }));
  }

  @Test
  public void testLoop() throws Exception {
    buildAndCheckIR("loop", checkBuilderDetection(builders -> {
      assertEquals(2, builders.size());
    }));
  }

  @Test
  public void testLoopWithBuilder() throws Exception {
    buildAndCheckIR("loopWithBuilder", checkBuilderDetection(builders -> {
      assertEquals(1, builders.size());
    }));
  }

  // TODO(b/114002137): later, test what const-string is computed for builders.
  private Consumer<IRCode> checkBuilderDetection(Consumer<Set<Value>> builderConsumer) {
    return code -> {
      StringBuilderOptimizer optimizer =
          new StringBuilderOptimizer(
              appView, new StringBuilderOptimizationConfigurationForTesting());
      Set<Value> builders = optimizer.computeTrivialStringConcatenation(code);
      builderConsumer.accept(builders);
    };
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
    public boolean isBuilderInit(DexType builderType, DexMethod method) {
      return builderType == method.holder
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
