// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugaring.interfacemethods;

import static com.android.tools.r8.TestRuntime.getCheckedInJdk;
import static com.android.tools.r8.TestRuntime.getCheckedInJdk11;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugaring.interfacemethods.methodparameters.I;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MethodParametersTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    // java.lang.reflect.Method.getParameters() supported from Android 8.1.
    return getTestParameters()
        .withDexRuntimesStartingFromIncluding(Version.V8_1_0)
        .withCfRuntimes()
        .withAllApiLevelsAlsoForCf()
        .build();
  }

  public MethodParametersTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testJvm() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    testForJvm()
        .addProgramClassesAndInnerClasses(I.class)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), TestRunner.class)
        .assertSuccessWithOutputLines("0", "1", "2", "0", "1", "2");
  }

  @Test
  public void testDesugar() throws Exception {
    // JDK8 is not present on Windows.
    assumeTrue(
        parameters.isDexRuntime()
            || getCheckedInJdk(parameters.getRuntime().asCf().getVm()) != null);
    Path compiledWithParameters =
        javac(
                parameters.isCfRuntime()
                    ? getCheckedInJdk(parameters.getRuntime().asCf().getVm())
                    : getCheckedInJdk11())
            .addSourceFiles(ToolHelper.getSourceFileForTestClass(I.class))
            .addOptions("-parameters")
            .compile();

    Path interfaceDesugared =
        testForD8(Backend.CF)
            .addProgramFiles(compiledWithParameters)
            .setMinApi(parameters.getApiLevel())
            .compile()
            .writeToZip();

    Path interfaceDesugaredTwice =
        testForD8(Backend.CF)
            .addProgramFiles(interfaceDesugared)
            .setMinApi(parameters.getApiLevel())
            .compile()
            // TODO(b/189743726): These warnings should not be there.
            .applyIf(
                parameters.canUseDefaultAndStaticInterfaceMethodsWhenDesugaring(),
                TestCompileResult::assertNoInfoMessages,
                r ->
                    r.assertAtLeastOneInfoMessage()
                        .assertAllInfoMessagesMatch(
                            anyOf(
                                containsString(
                                    "Invalid parameter counts in MethodParameter attributes"),
                                containsString("Methods with invalid MethodParameter attributes"))))
            .writeToZip();

    Path programDesugared =
        testForD8(Backend.CF)
            .addClasspathClasses(I.class)
            .addInnerClasses(getClass())
            .setMinApi(parameters.getApiLevel())
            .compile()
            .writeToZip();

    testForD8(parameters.getBackend())
        .addProgramFiles(interfaceDesugaredTwice)
        .addProgramFiles(programDesugared)
        .setMinApi(parameters.getApiLevel())
        .compile()
        // TODO(b/189743726): These warnings should not be there.
        .applyIf(
            parameters.canUseDefaultAndStaticInterfaceMethodsWhenDesugaring(),
            TestCompileResult::assertNoInfoMessages,
            r ->
                r.assertAtLeastOneInfoMessage()
                    .assertAllInfoMessagesMatch(
                        anyOf(
                            containsString(
                                "Invalid parameter counts in MethodParameter attributes"),
                            containsString("Methods with invalid MethodParameter attributes"))))
        .run(parameters.getRuntime(), TestRunner.class)
        .applyIf(
            parameters.canUseDefaultAndStaticInterfaceMethodsWhenDesugaring(),
            r -> r.assertSuccessWithOutputLines("0", "1", "2", "0", "1", "2"),
            // TODO(b/189743726): Should not fail at runtime (but will have different parameter
            // count for non-static methods when desugared).
            r ->
                r.assertFailureWithErrorThatMatches(
                    containsString("Wrong number of parameters in MethodParameters attribute")));
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
