// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.StringUtils;
import java.util.function.Function;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class FunctionTest extends TestBase {

  private static final String expectedOutput = StringUtils.lines("Hello, world", "Hello, world");
  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimesStartingFromIncluding(Version.V8_1_0).build();
  }

  public FunctionTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testD8() throws Exception {
    testForD8()
        .addInnerClasses(FunctionTest.class)
        .run(parameters.getRuntime(), FunctionTest.TestClass.class)
        .assertSuccessWithOutput(expectedOutput);
  }

  @Test
  public void testR8Working() throws Exception {
    testForR8(parameters.getBackend())
        .addKeepMainRule(TestClass.class)
        .noTreeShaking()
        .noMinification()
        .enableInliningAnnotations()
        .addInnerClasses(FunctionTest.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(expectedOutput);
  }

  @Test
  public void testR8WithTreeshaking() throws Exception {
    testForR8(parameters.getBackend())
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .addInnerClasses(FunctionTest.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(expectedOutput);
  }

  static class TestClass {

    @NeverInline
    private static String applyFunction(Function<String, String> f) {
      return f.apply("Hello, world");
    }

    public static void main(String[] args) {
      System.out.println(applyFunction(s -> s + System.lineSeparator() + s));
    }
  }
}
