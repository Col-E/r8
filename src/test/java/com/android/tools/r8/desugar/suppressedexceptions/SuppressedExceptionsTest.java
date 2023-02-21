// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.suppressedexceptions;

import static com.android.tools.r8.desugar.suppressedexceptions.TwrSuppressedExceptionsTest.getInvokesTo;
import static com.android.tools.r8.desugar.suppressedexceptions.TwrSuppressedExceptionsTest.hasInvokesTo;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.DesugarTestConfiguration;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.IntBox;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SuppressedExceptionsTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  public boolean runtimeHasSuppressedExceptionsSupport() {
    // TODO(b/214239152): Update this if desugaring is changed.
    // Despite 4.0.4 being API level 15 and add suppressed being officially added in 19 it is
    // actually implemented. Thus, the backport implementation will use the functionality and run
    // as expected by RI.
    return parameters.isCfRuntime()
        || parameters.getDexRuntimeVersion().isNewerThanOrEqual(Version.V4_0_4);
  }

  public boolean apiLevelHasSuppressedExceptionsSupport(boolean isDesugaring) {
    return !isDesugaring
        || parameters
            .getApiLevel()
            .isGreaterThanOrEqualTo(apiLevelWithSuppressedExceptionsSupport());
  }

  @Test
  public void testD8() throws Exception {
    testForDesugaring(parameters)
        .addProgramClasses(TestClass.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(
            runtimeHasSuppressedExceptionsSupport() ? StringUtils.lines("FOO") : "NONE")
        .inspectIf(
            DesugarTestConfiguration::isDesugared,
            inspector ->
                hasInvokesTo(
                    inspector.clazz(TestClass.class).uniqueMethodWithOriginalName("main"),
                    "getSuppressed",
                    apiLevelHasSuppressedExceptionsSupport(true) ? 1 : 0))
        .inspectIf(
            DesugarTestConfiguration::isNotDesugared,
            inspector ->
                hasInvokesTo(
                    inspector.clazz(TestClass.class).uniqueMethodWithOriginalName("main"),
                    "getSuppressed",
                    1));
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addInnerClasses(SuppressedExceptionsTest.class)
        .setMinApi(parameters)
        .addKeepMainRule(TestClass.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(
            runtimeHasSuppressedExceptionsSupport() ? StringUtils.lines("FOO") : "NONE")
        .inspect(
            inspector -> {
              hasInvokesTo(
                  inspector.clazz(TestClass.class).uniqueMethodWithOriginalName("main"),
                  "getSuppressed",
                  apiLevelHasSuppressedExceptionsSupport(parameters.isDexRuntime()) ? 1 : 0);
              IntBox gets = new IntBox(0);
              IntBox adds = new IntBox(0);
              inspector.forAllClasses(
                  c ->
                      c.forAllMethods(
                          m -> {
                            gets.increment(getInvokesTo(m, "getSuppressed").size());
                            adds.increment(getInvokesTo(m, "addSuppressed").size());
                          }));
              if (apiLevelHasSuppressedExceptionsSupport(parameters.isDexRuntime())) {
                assertEquals(1, gets.get());
                assertEquals(1, adds.get());
              } else {
                assertEquals(0, gets.get());
                assertEquals(0, adds.get());
              }
            });
  }

  static class TestClass {

    public static void foo() {
      throw new RuntimeException("FOO");
    }

    public static void bar() {
      try {
        foo();
      } catch (RuntimeException e) {
        RuntimeException bar = new RuntimeException("BAR");
        bar.addSuppressed(e);
        throw bar;
      }
    }

    public static void main(String[] args) {
      try {
        bar();
      } catch (RuntimeException e) {
        Throwable[] suppressed = e.getSuppressed();
        if (suppressed.length == 0) {
          System.out.println("NONE");
        } else {
          for (Throwable throwable : suppressed) {
            System.out.println(throwable.getMessage());
          }
        }
      }
    }
  }
}
