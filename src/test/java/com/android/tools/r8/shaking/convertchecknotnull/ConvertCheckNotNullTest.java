// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.convertchecknotnull;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeMatchers;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.Objects;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ConvertCheckNotNullTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .applyIf(
            parameters.isDexRuntime(),
            testBuilder -> testBuilder.addLibraryFiles(ToolHelper.getMostRecentAndroidJar()))
        .addKeepMainRule(Main.class)
        .addKeepRules(
            "-convertchecknotnull class " + Main.class.getTypeName() + " {",
            "  void requireNonNullWithoutReturn(**, ...);",
            "  ** requireNonNullWithReturn(**, ...);",
            "}",
            "-convertchecknotnull class java.util.Objects {",
            "  ** requireNonNull(**, ...);",
            "}")
        .enableExperimentalConvertCheckNotNull()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject mainClassSubject = inspector.clazz(Main.class);
              assertThat(mainClassSubject, isPresent());

              MethodSubject mainMethodSubject = mainClassSubject.mainMethod();
              assertThat(mainMethodSubject, isPresent());
              assertEquals(
                  parameters.isDexRuntime()
                          && parameters.getApiLevel().isLessThan(AndroidApiLevel.K)
                      ? 4
                      : 6,
                  mainMethodSubject
                      .streamInstructions()
                      .filter(
                          CodeMatchers.isInvokeWithTarget(Object.class.getTypeName(), "getClass"))
                      .count());

              assertThat(
                  mainClassSubject.uniqueMethodWithOriginalName("requireNonNullWithoutReturn"),
                  isAbsent());
              assertThat(
                  mainClassSubject.uniqueMethodWithOriginalName("requireNonNullWithReturn"),
                  isAbsent());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(getExpectedOutput());
  }

  private String getExpectedOutput() {
    String message4 = "null";
    String message5 = "null";
    String message6 = "null";
    if (parameters.isCfRuntime()) {
      if (parameters.getRuntime().asCf().isNewerThanOrEqual(CfVm.JDK17)) {
        message4 = "Cannot invoke \"Object.getClass()\" because \"<local3>\" is null";
        message5 = "Cannot invoke \"Object.getClass()\" because \"<local4>\" is null";
        message6 = "Cannot invoke \"Object.getClass()\" because \"<local5>\" is null";
      }
    } else {
      if (parameters.getDexRuntimeVersion().isEqualToOneOf(Version.V8_1_0, Version.DEFAULT)) {
        if (parameters.getApiLevel().isLessThan(AndroidApiLevel.K)) {
          message4 = message5 = "Attempt to invoke a virtual method on a null object reference";
          message6 =
              "Attempt to invoke virtual method 'java.lang.Class"
                  + " java.lang.Object.getClass()' on a null object reference";

        } else {
          message4 =
              message5 = message6 = "Attempt to invoke a virtual method on a null object reference";
        }
      } else if (parameters.getDexRuntimeVersion().isNewerThanOrEqual(Version.V5_1_1)) {
        message4 =
            message5 =
                message6 =
                    "Attempt to invoke virtual method 'java.lang.Class"
                        + " java.lang.Object.getClass()' on a null object reference";
      }
    }
    return StringUtils.lines(
        "Test #1", "Test #2", "Test #3", "Test #4", message4, "Test #5", message5, "Test #6",
        message6);
  }

  static class Main {

    public static void main(String[] args) {
      Object p1 = System.currentTimeMillis() >= 0 ? new Object() : null;
      Object p2 = System.currentTimeMillis() >= 0 ? new Object() : null;
      Object p3 = System.currentTimeMillis() >= 0 ? new Object() : null;
      Object p4 = System.currentTimeMillis() >= 0 ? null : new Object();
      Object p5 = System.currentTimeMillis() >= 0 ? null : new Object();
      Object p6 = System.currentTimeMillis() >= 0 ? null : new Object();

      System.out.println("Test #1");
      requireNonNullWithoutReturn(p1, "p1");

      System.out.println("Test #2");
      Object p2alias = requireNonNullWithReturn(p2, "p2");
      if (p2alias != p2) {
        throw new RuntimeException();
      }

      System.out.println("Test #3");
      Object p3alias = Objects.requireNonNull(p3, "p3");
      if (p3alias != p3) {
        throw new RuntimeException();
      }

      System.out.println("Test #4");
      try {
        requireNonNullWithoutReturn(p4, "p4");
      } catch (NullPointerException e) {
        System.out.println(e.getMessage());
      }

      System.out.println("Test #5");
      try {
        Object p5alias = requireNonNullWithReturn(p5, "p5");
        System.out.println(p5alias);
      } catch (NullPointerException e) {
        System.out.println(e.getMessage());
      }

      System.out.println("Test #6");
      try {
        Object p6alias = Objects.requireNonNull(p6, "p6");
        System.out.println(p6alias);
      } catch (NullPointerException e) {
        System.out.println(e.getMessage());
      }
    }

    static void requireNonNullWithoutReturn(Object object, String parameterName) {
      if (object == null) {
        throw new NullPointerException("Expected parameter " + parameterName + " to be non-null");
      }
    }

    static Object requireNonNullWithReturn(Object object, String parameterName) {
      if (object == null) {
        throw new NullPointerException("Expected parameter " + parameterName + " to be non-null");
      }
      return object;
    }
  }
}
