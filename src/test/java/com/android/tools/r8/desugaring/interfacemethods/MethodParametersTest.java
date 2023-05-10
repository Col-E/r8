// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugaring.interfacemethods;

import static com.android.tools.r8.TestRuntime.getCheckedInJdk;
import static com.android.tools.r8.TestRuntime.getCheckedInJdk11;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugaring.interfacemethods.methodparameters.I;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.SupplierUtils;
import java.nio.file.Path;
import java.util.function.Supplier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MethodParametersTest extends TestBase {

  private final TestParameters parameters;
  private final Supplier<Path> compiledWithParameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    // java.lang.reflect.Method.getParameters() supported from Android 8.
    return getTestParameters()
        .withDexRuntimesStartingFromExcluding(Version.V7_0_0)
        .withCfRuntimes()
        .withAllApiLevelsAlsoForCf()
        .build();
  }

  public MethodParametersTest(TestParameters parameters) {
    this.parameters = parameters;
    compiledWithParameters =
        SupplierUtils.memoize(
            () ->
                javac(
                        parameters.isCfRuntime()
                            ? getCheckedInJdk(parameters.getRuntime().asCf().getVm())
                            : getCheckedInJdk11())
                    .addSourceFiles(ToolHelper.getSourceFileForTestClass(I.class))
                    .addOptions("-parameters")
                    .compile());
  }

  private final String EXPECTED =
      StringUtils.lines(
          "0", "1", "a: 1", "2", "a: 1", "b: 2", "0", "1", "a: 1", "2", "a: 1", "b: 2");
  private final String EXPECTED_DESUGARED =
      StringUtils.lines(
          "1",
          "2",
          "_this: 0",
          "a: 1",
          "3",
          "_this: 0",
          "a: 1",
          "b: 2",
          "0",
          "1",
          "a: 1",
          "2",
          "a: 1",
          "b: 2");

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramFiles(compiledWithParameters.get())
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), TestRunner.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testDesugar() throws Exception {
    // JDK8 is not present on Windows.
    assumeTrue(
        parameters.isDexRuntime()
            || getCheckedInJdk(parameters.getRuntime().asCf().getVm()) != null);
    Path interfaceDesugared =
        testForD8(Backend.CF)
            .addProgramFiles(compiledWithParameters.get())
            .setMinApi(parameters)
            .compile()
            .writeToZip();

    Path interfaceDesugaredTwice =
        testForD8(Backend.CF)
            .addProgramFiles(interfaceDesugared)
            .setMinApi(parameters)
            .compile()
            .writeToZip();

    Path programDesugared =
        testForD8(Backend.CF)
            .addClasspathClasses(I.class)
            .addInnerClasses(getClass())
            .setMinApi(parameters)
            .compile()
            .writeToZip();

    testForD8(parameters.getBackend())
        .addProgramFiles(interfaceDesugaredTwice)
        .addProgramFiles(programDesugared)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), TestRunner.class)
        .applyIf(
            parameters.canUseDefaultAndStaticInterfaceMethodsWhenDesugaring(),
            r -> r.assertSuccessWithOutput(EXPECTED),
            r -> r.assertSuccessWithOutput(EXPECTED_DESUGARED));
  }

  static class A implements I {}

  static class TestRunner {

    public static void main(String[] args) {
      new A().zeroArgsDefault();
      new A().oneArgDefault(0);
      new A().twoArgDefault(0, 0);
      I.zeroArgStatic();
      I.oneArgStatic(0);
      I.twoArgsStatic(0, 0);
    }
  }
}
