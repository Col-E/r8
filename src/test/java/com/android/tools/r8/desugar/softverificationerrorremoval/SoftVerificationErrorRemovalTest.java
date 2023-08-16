// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.softverificationerrorremoval;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.D8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.desugar.LibraryFilesHelper;
import java.util.function.Supplier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class SoftVerificationErrorRemovalTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public SoftVerificationErrorRemovalTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testWithoutJavaStub() throws Exception {
    D8TestRunResult run =
        testForD8()
            .addInnerClasses(SoftVerificationErrorRemovalTest.class)
            .setMinApi(parameters)
            .compile()
            .run(parameters.getRuntime(), TestClass.class);
    assertVerificationErrorsPresent(
        run.getStdErr(),
        parameters.getDexRuntimeVersion().isOlderThanOrEqual(ToolHelper.DexVm.Version.V4_4_4));
  }

  private void assertVerificationErrorsPresent(String stdErr, boolean present) {
    assertEquals(
        present,
        stdErr.contains(
            "VFY: unable to find class referenced in signature (Ljava/util/function/Supplier;)"));
    assertEquals(
        present,
        stdErr.contains(
            "VFY: unable to resolve interface method 7: Ljava/util/function/Supplier;.get"
                + " ()Ljava/lang/Object;"));
  }

  @Test
  public void testWithJavaStub() throws Exception {
    D8TestRunResult run =
        testForD8()
            .addInnerClasses(SoftVerificationErrorRemovalTest.class)
            .addProgramClassFileData(LibraryFilesHelper.getSupplier())
            .setMinApi(parameters)
            .compile()
            .run(parameters.getRuntime(), TestClass.class);
    assertVerificationErrorsPresent(run.getStdErr(), false);
  }

  static class TestClass {

    public static void main(String[] args) {
      ExampleClass exampleClass = new ExampleClass();
      exampleClass.hello();
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
