// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.staticinterfacemethod;

import static com.android.tools.r8.desugar.staticinterfacemethod.InvokeStaticDesugarTest.Library.foo;
import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.ToolHelper.DexVm;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvokeStaticDesugarTest extends TestBase {

  private final TestParameters parameters;
  private final String EXPECTED = "Hello World!";

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  public InvokeStaticDesugarTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testDesugar() throws Exception {
    final TestRunResult<?> runResult =
        testForDesugaring(parameters)
            .addLibraryClasses(Library.class)
            .addProgramClasses(Main.class)
            .addRunClasspathFiles(compileRunClassPath())
            .run(parameters.getRuntime(), Main.class);
    if (parameters.isDexRuntime()
        && parameters.getRuntime().asDex().getVm().isOlderThanOrEqual(DexVm.ART_4_4_4_HOST)) {
      runResult.assertFailureWithErrorThatMatches(containsString("java.lang.VerifyError"));
    } else {
      runResult.assertSuccessWithOutputLines(EXPECTED);
    }
  }

  private Path compileRunClassPath() throws Exception {
    if (parameters.isCfRuntime()) {
      return compileToZip(parameters, ImmutableList.of(), Library.class);
    } else {
      assert parameters.isDexRuntime();
      return testForD8(parameters.getBackend())
          .addProgramClasses(Library.class)
          .setMinApi(parameters.getApiLevel())
          .disableDesugaring()
          .addOptionsModification(
              options -> {
                options.testing.allowStaticInterfaceMethodsForPreNApiLevel = true;
              })
          .compile()
          .writeToZip();
    }
  }

  public interface Library {

    static void foo() {
      System.out.println("Hello World!");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      foo();
    }
  }
}
