// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import com.android.tools.r8.LibraryDesugaringTestConfiguration;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.Locale;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MonthTest extends DesugaredLibraryTestBase {
  private final TestParameters parameters;

  private static final String EXPECTED_JAVA_8_OUTPUT = StringUtils.lines("4");
  private static final String EXPECTED_JAVA_9_OUTPUT = StringUtils.lines("April");

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  public MonthTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private String getExpectedResult(TestParameters parameters) {
    if (parameters.isCfRuntime()) {
      if (parameters.getRuntime().asCf().isNewerThan(TestRuntime.CfVm.JDK8)) {
        return EXPECTED_JAVA_9_OUTPUT;
      }
      return EXPECTED_JAVA_8_OUTPUT;
    }
    assert parameters.isDexRuntime();
    // Assumes java.time is desugared only if any library desugaring is required, i.e., on 26.
    if (requiresAnyCoreLibDesugaring(parameters)) {
      return EXPECTED_JAVA_8_OUTPUT;
    }
    return EXPECTED_JAVA_9_OUTPUT;
  }

  @Test
  public void testMonthD8() throws Exception {
    if (parameters.isCfRuntime()) {
      testForJvm()
          .addInnerClasses(MonthTest.class)
          .run(parameters.getRuntime(), MonthTest.Main.class)
          .assertSuccessWithOutput(getExpectedResult(parameters));
      return;
    }
    testForD8(parameters.getBackend())
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .addInnerClasses(MonthTest.class)
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(
            LibraryDesugaringTestConfiguration.forApiLevel(parameters.getApiLevel()))
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(getExpectedResult(parameters));
  }

  @Test
  public void testMonthR8() throws Exception {
    Assume.assumeTrue(parameters.isDexRuntime());
    testForR8(parameters.getBackend())
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .addInnerClasses(MonthTest.class)
        .addKeepMainRule(MonthTest.Main.class)
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(
            LibraryDesugaringTestConfiguration.forApiLevel(parameters.getApiLevel()))
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(getExpectedResult(parameters));
  }

  static class Main {
    public static void main(String[] args) {
      System.out.println(Month.APRIL.getDisplayName(TextStyle.FULL_STANDALONE, Locale.UK));
    }
  }
}
