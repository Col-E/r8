// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ir.code.IRCode;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DeterminismAnalysisTest extends AnalysisTestBase {

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public DeterminismAnalysisTest(TestParameters parameters) throws Exception {
    super(parameters, TestClass.class.getTypeName(), TestClass.class);
  }

  @Test
  public void testMax() {
    buildAndCheckIR("max", checkAnalysisResult(true));
  }

  @Test
  public void testSget() {
    buildAndCheckIR("sget", checkAnalysisResult(false));
  }

  @Test
  public void testIget() {
    buildAndCheckIR("iget", checkAnalysisResult(false));
  }

  @Test
  public void testAget() {
    buildAndCheckIR("aget", checkAnalysisResult(false));
  }

  @Test
  public void testAlength() {
    buildAndCheckIR("alength", checkAnalysisResult(false));
  }

  @Test
  public void testCreateInstance() {
    buildAndCheckIR("createInstance", checkAnalysisResult(false));
  }

  @Test
  public void testCreateArray() {
    buildAndCheckIR("createArray", checkAnalysisResult(false));
  }

  private Consumer<IRCode> checkAnalysisResult(boolean expectedResult) {
    return code -> {
      assertEquals(expectedResult,
          DeterminismAnalysis.returnValueOnlyDependsOnArguments(appView.withLiveness(), code));
    };
  }

  static class TestClass {

    static int ID;
    Object field;

    static int max(int x, int y) {
      return x > y ? x : y;
    }

    static int sget() {
      return ID;
    }

    static Object iget(TestClass instance) {
      return instance != null ? instance.field : null;
    }

    static TestClass aget(TestClass[] args, int i) {
      return args != null ? args[i] : null;
    }

    static int alength(TestClass[] args) {
      return args.length;
    }

    static TestClass createInstance(Object field) {
      TestClass instance = new TestClass();
      instance.field = field;
      return instance;
    }

    static TestClass[] createArray(int size, Object[] fields) {
      TestClass[] array = new TestClass[size];
      for (int i = 0; i < size; i++) {
        array[i].field = fields[i];
      }
      return array;
    }
  }
}
