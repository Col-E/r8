// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TestBuilderMinAndroidJarTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withAllRuntimes()
        .withApiLevelsStartingAtIncluding(AndroidApiLevel.N)
        .build();
  }

  public TestBuilderMinAndroidJarTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testSupplierD8NotSupported()
      throws ExecutionException, CompilationFailedException, IOException {
    assumeTrue(parameters.isDexRuntime());
    assumeTrue(parameters.getRuntime().asDex().getVm().isOlderThanOrEqual(DexVm.ART_6_0_1_HOST));
    testForD8()
        .addProgramClasses(Main.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatMatches(containsString("NoClassDefFoundError"));
  }

  @Test
  public void testSupplierR8NotSupported()
      throws ExecutionException, CompilationFailedException, IOException {
    assumeFalse(
        parameters.isCfRuntime()
            || parameters.getApiLevel().getLevel() >= AndroidApiLevel.N.getLevel());
    Matcher<String> expectedError =
        parameters.getRuntime().asDex().getVm().isOlderThanOrEqual(DexVm.ART_6_0_1_HOST)
            ? containsString("NoClassDefFoundError")
            : containsString("AbstractMethodError");
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class)
        .allowDiagnosticWarningMessages(
            parameters.isDexRuntime() && parameters.getApiLevel().isLessThan(AndroidApiLevel.O))
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .compile()
        .assertAllWarningMessagesMatch(
            anyOf(
                equalTo(
                    "Lambda expression implements missing interface `java.util.function.Supplier`"),
                containsString("required for default or static interface methods desugaring")))
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatMatches(expectedError);
  }

  @Test
  public void testSupplierD8Supported()
      throws ExecutionException, CompilationFailedException, IOException {
    assumeTrue(parameters.isDexRuntime());
    assumeTrue(parameters.getRuntime().asDex().getVm().isNewerThan(DexVm.ART_6_0_1_HOST));
    testForD8()
        .addProgramClasses(Main.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello World!");
  }

  @Test
  public void testSupplierR8Supported()
      throws ExecutionException, CompilationFailedException, IOException {
    assumeTrue(
        parameters.isCfRuntime()
            || parameters.getApiLevel().getLevel() >= AndroidApiLevel.N.getLevel());
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class)
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello World!");
  }

  public static class Main {

    public static void main(String[] args) {
      test(() -> "Hello World!");
    }

    public static void test(Supplier<String> supplier) {
      System.out.println(supplier.get());
    }
  }
}
