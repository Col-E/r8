// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.callsites;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import java.util.List;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CallSiteOptimizationLibraryLambdaPropagationTest extends TestBase {

  private final boolean enableExperimentalArgumentPropagation;
  private final TestParameters parameters;

  @Parameters(name = "{1}, experimental: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(),
        getTestParameters()
            .withCfRuntimes()
            .withDexRuntimes()
            .withApiLevelsStartingAtIncluding(AndroidApiLevel.N)
            .build());
  }

  public CallSiteOptimizationLibraryLambdaPropagationTest(
      boolean enableExperimentalArgumentPropagation, TestParameters parameters) {
    this.enableExperimentalArgumentPropagation = enableExperimentalArgumentPropagation;
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(CallSiteOptimizationLibraryLambdaPropagationTest.class)
        .addKeepMainRule(TestClass.class)
        .applyIf(
            enableExperimentalArgumentPropagation,
            builder ->
                builder.addOptionsModification(
                    options ->
                        options
                            .callSiteOptimizationOptions()
                            .setEnableExperimentalArgumentPropagation()))
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("A", "B");
  }

  static class TestClass {

    public static void main(String[] args) {
      add(new A());
      Consumer<Object> consumer = TestClass::add;
      consumer.accept("B");
    }

    @NeverInline
    static void add(Object o) {
      System.out.println(o.toString());
    }
  }

  @NeverClassInline
  static class A {

    @Override
    public String toString() {
      return "A";
    }
  }
}
