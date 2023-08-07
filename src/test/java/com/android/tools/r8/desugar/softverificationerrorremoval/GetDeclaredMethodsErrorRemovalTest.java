// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.softverificationerrorremoval;

import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.D8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.desugar.LibraryFilesHelper;
import java.util.Arrays;
import java.util.function.Supplier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class GetDeclaredMethodsErrorRemovalTest extends TestBase {

  private final TestParameters parameters;
  private static final String TYPE =
      "com.android.tools.r8.desugar.softverificationerrorremoval."
          + "GetDeclaredMethodsErrorRemovalTest";
  private static final String EXPECTED_RESULT =
      "[void"
          + " "
          + TYPE
          + "$ExampleClass.hello(),"
          + " void"
          + " "
          + TYPE
          + "$ExampleClass.hello(java.util.function.Supplier)]";

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public GetDeclaredMethodsErrorRemovalTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testWithoutJavaStub() throws Exception {
    D8TestRunResult run =
        testForD8()
            .addInnerClasses(GetDeclaredMethodsErrorRemovalTest.class)
            .setMinApi(parameters)
            .compile()
            .run(parameters.getRuntime(), TestClass.class);
    if (parameters.getDexRuntimeVersion().isOlderThanOrEqual(ToolHelper.DexVm.Version.V6_0_1)) {
      run.assertFailureWithErrorThatMatches(containsString("java.lang.NoClassDefFoundError"));
    } else {
      run.assertSuccessWithOutputLines(EXPECTED_RESULT);
    }
  }

  @Test
  public void testWithJavaStub() throws Exception {
    testForD8()
        .addInnerClasses(GetDeclaredMethodsErrorRemovalTest.class)
        .addProgramClassFileData(LibraryFilesHelper.getSupplier())
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(EXPECTED_RESULT);
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(Arrays.toString(ExampleClass.class.getDeclaredMethods()));
    }
  }

  static class ExampleClass {
    void hello() {
      System.out.println("hello");
    }

    void hello(Supplier<String> stringSupplier) {
      System.out.println(stringSupplier.get());
    }
  }
}
