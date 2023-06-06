// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.backports;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.utils.AndroidApiLevel.LATEST;
import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.util.function.Supplier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ThreadLocalBackportTest extends DesugaredLibraryTestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  private static final String EXPECTED_OUTPUT = StringUtils.lines("Hello, ", "world!");

  private void checkDiagnostics(TestDiagnosticMessages diagnostics) {
    if (parameters.isDexRuntime() && parameters.getApiLevel().isLessThan(AndroidApiLevel.N)) {
      diagnostics.assertWarningsMatch(
          diagnosticMessage(containsString("Type `java.util.function.Supplier` was not found")));
    } else {
      diagnostics.assertNoMessages();
    }
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .compileWithExpectedDiagnostics(this::checkDiagnostics)
        .run(parameters.getRuntime(), TestClass.class)
        .apply(this::checkExpected);
  }

  @Test
  public void testIntermediateD8() throws Exception {
    D8TestCompileResult intermediate =
        testForD8(parameters.getBackend())
            .addInnerClasses(getClass())
            .setOutputMode(
                parameters.isCfRuntime() ? OutputMode.ClassFile : OutputMode.DexFilePerClass)
            .setMinApi(parameters)
            .compileWithExpectedDiagnostics(this::checkDiagnostics);

    testForD8(parameters.getBackend())
        .addProgramFiles(intermediate.writeToZip())
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .apply(this::checkExpected);
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addLibraryFiles(
            parameters.isCfRuntime()
                ? ToolHelper.getJava8RuntimeJar()
                : ToolHelper.getAndroidJar(LATEST))
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .apply(this::checkExpected);
  }

  private void checkExpected(TestRunResult<?> result) {
    result.applyIf(
        parameters.getRuntime().isCf()
            || parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.N)
            || parameters
                .getRuntime()
                .asDex()
                .getMinApiLevel()
                .isGreaterThanOrEqualTo(AndroidApiLevel.O),
        r -> r.assertSuccessWithOutput(EXPECTED_OUTPUT),
        parameters.getRuntime().isDex()
            && parameters
                .getRuntime()
                .asDex()
                .getMinApiLevel()
                .isGreaterThanOrEqualTo(AndroidApiLevel.N)
            && parameters.getRuntime().asDex().getMinApiLevel().isLessThan(AndroidApiLevel.O)
            && parameters.getApiLevel().isLessThan(AndroidApiLevel.N),
        r -> r.assertFailureWithErrorThatThrows(NoSuchMethodError.class),
        r -> r.assertFailureWithErrorThatThrows(NoClassDefFoundError.class));
  }

  static class TestClass {

    public static ThreadLocal<String> createThreadLocalWithInitial(Supplier<String> supplier) {
      return ThreadLocal.withInitial(supplier);
    }

    public static void main(String[] args) {
      ThreadLocal<String> threadLocal = ThreadLocal.withInitial(() -> "Hello, ");
      System.out.println(threadLocal.get());
      System.out.println(createThreadLocalWithInitial(() -> "world!").get());
    }
  }
}
