// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.suppressedexceptions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.DesugarTestConfiguration;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.examples.JavaExampleClassProxy;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.IntBox;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class TwrSuppressedExceptionsTest extends TestBase {

  private static final String PKG = "twraddsuppressed";
  private static final String EXAMPLE = "examplesJava9/" + PKG;
  private final JavaExampleClassProxy MAIN = new JavaExampleClassProxy(EXAMPLE, PKG + ".TestClass");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK9)
        .withDexRuntimes()
        .withAllApiLevelsAlsoForCf()
        .build();
  }

  public TwrSuppressedExceptionsTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  public boolean runtimeHasSuppressedExceptionsSupport() {
    // TODO(b/214239152): Update this if desugaring is changed.
    // Despite 4.0.4 being API level 15 and add suppressed being officially added in 19 it is
    // actually implemented. Thus, the backport implementation will use the functionality and run
    // as expected by RI.
    return parameters.isCfRuntime()
        || parameters.getDexRuntimeVersion().isNewerThanOrEqual(Version.V4_0_4);
  }

  public boolean apiLevelHasSuppressedExceptionsSupport() {
    return parameters
        .getApiLevel()
        .isGreaterThanOrEqualTo(apiLevelWithSuppressedExceptionsSupport());
  }

  public boolean apiLevelHasTwrCloseResourceSupport() {
    return parameters.getApiLevel().isGreaterThanOrEqualTo(apiLevelWithTwrCloseResourceSupport());
  }

  public List<Path> getProgramInputs() {
    return ImmutableList.of(JavaExampleClassProxy.examplesJar(EXAMPLE));
  }

  @Test
  public void testD8() throws Exception {
    testForDesugaring(parameters)
        .addProgramFiles(getProgramInputs())
        .run(parameters.getRuntime(), MAIN.typeName())
        .assertSuccessWithOutput(
            runtimeHasSuppressedExceptionsSupport() ? StringUtils.lines("CLOSE") : "NONE")
        .inspectIf(
            DesugarTestConfiguration::isDesugared,
            inspector -> {
              ClassSubject clazz = inspector.clazz(MAIN.typeName());
              hasInvokesTo(
                  clazz.uniqueMethodWithOriginalName("bar"),
                  "$closeResource",
                  apiLevelHasTwrCloseResourceSupport() ? 4 : 0);
              if (apiLevelHasSuppressedExceptionsSupport()) {
                hasInvokesTo(clazz.mainMethod(), "getSuppressed", 1);
              } else {
                inspector.forAllClasses(
                    c ->
                        c.forAllMethods(
                            m -> {
                              hasInvokesTo(m, "getSuppressed", 0);
                              hasInvokesTo(m, "addSuppressed", 0);
                            }));
              }
            })
        .inspectIf(
            DesugarTestConfiguration::isNotDesugared,
            inspector -> {
              ClassSubject clazz = inspector.clazz(MAIN.typeName());
              hasInvokesTo(clazz.uniqueMethodWithOriginalName("bar"), "$closeResource", 4);
              hasInvokesTo(clazz.mainMethod(), "getSuppressed", 1);
            });
  }

  @Test
  public void testR8() throws Exception {
    assumeTrue(
        "R8 does not desugar CF so only run the high API variant.",
        parameters.isDexRuntime() || parameters.getApiLevel().isGreaterThan(AndroidApiLevel.B));
    testForR8(parameters.getBackend())
        .addProgramFiles(getProgramInputs())
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(MAIN.typeName())
        // TODO(b/214250388): Don't warn about AutoClosable in synthesized code.
        .apply(
            b -> {
              if (!parameters.isCfRuntime() && !apiLevelHasTwrCloseResourceSupport()) {
                b.addDontWarn(AutoCloseable.class);
              }
            })
        .run(parameters.getRuntime(), MAIN.typeName())
        .assertSuccessWithOutput(
            runtimeHasSuppressedExceptionsSupport() ? StringUtils.lines("CLOSE") : "NONE")
        .inspect(
            inspector -> {
              IntBox gets = new IntBox(0);
              IntBox adds = new IntBox(0);
              inspector.forAllClasses(
                  c ->
                      c.forAllMethods(
                          m -> {
                            gets.increment(getInvokesTo(m, "getSuppressed").size());
                            adds.increment(getInvokesTo(m, "addSuppressed").size());
                          }));
              if (apiLevelHasSuppressedExceptionsSupport()) {
                hasInvokesTo(inspector.clazz(MAIN.typeName()).mainMethod(), "getSuppressed", 1);
                assertEquals(1, gets.get());
                assertEquals(1, adds.get());
              } else {
                assertEquals(0, gets.get());
                assertEquals(0, adds.get());
              }
            });
  }

  public static void hasInvokesTo(MethodSubject method, String callee, int count) {
    List<InstructionSubject> getSuppressedCalls = getInvokesTo(method, callee);
    assertEquals(count, getSuppressedCalls.size());
  }

  public static List<InstructionSubject> getInvokesTo(MethodSubject method, String callee) {
    return method
        .streamInstructions()
        .filter(i -> i.isInvoke() && i.getMethod().getName().toString().equals(callee))
        .collect(Collectors.toList());
  }
}
