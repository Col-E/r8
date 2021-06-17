// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.time.chrono.Chronology;
import java.util.List;
import java.util.Map;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StaticInterfaceMethodTest extends DesugaredLibraryTestBase {

  private static final String EXPECTED_OUTPUT = StringUtils.lines("false", "java.util.HashSet");

  private final TestParameters parameters;
  private final boolean shrinkDesugaredLibrary;

  @Parameters(name = "{0}, shrinkDesugaredLibrary: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build(),
        BooleanUtils.values());
  }

  public StaticInterfaceMethodTest(TestParameters parameters, boolean shrinkDesugaredLibrary) {
    this.shrinkDesugaredLibrary = shrinkDesugaredLibrary;
    this.parameters = parameters;
  }

  @Test
  public void testStaticInterfaceMethodsD8() throws Exception {
    if (parameters.isCfRuntime()) {
      testForJvm()
          .addInnerClasses(StaticInterfaceMethodTest.class)
          .run(parameters.getRuntime(), Executor.class)
          .assertSuccessWithOutput(EXPECTED_OUTPUT);
      return;
    }
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForD8()
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .addInnerClasses(StaticInterfaceMethodTest.class)
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testStaticInterfaceMethodsD8CfToCf() throws Exception {
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    Path jar =
        testForD8(Backend.CF)
            .addInnerClasses(StaticInterfaceMethodTest.class)
            .setMinApi(parameters.getApiLevel())
            .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
            .compile()
            .writeToZip();

    String desugaredLibraryKeepRules = "";
    if (shrinkDesugaredLibrary && keepRuleConsumer.get() != null) {
      // Collection keep rules is only implemented in the DEX writer.
      assertEquals(0, keepRuleConsumer.get().length());
      desugaredLibraryKeepRules = "-keep class * { *; }";
    }
    if (parameters.getRuntime().isDex()) {
      testForD8()
          .addProgramFiles(jar)
          .setMinApi(parameters.getApiLevel())
          .disableDesugaring()
          .compile()
          .addDesugaredCoreLibraryRunClassPath(
              this::buildDesugaredLibrary,
              parameters.getApiLevel(),
              desugaredLibraryKeepRules,
              shrinkDesugaredLibrary)
          .run(parameters.getRuntime(), Executor.class)
          .assertSuccessWithOutput(EXPECTED_OUTPUT);
    } else {
      testForJvm()
          .addProgramFiles(jar)
          .addRunClasspathFiles(getDesugaredLibraryInCF(parameters, options -> {}))
          .run(parameters.getRuntime(), Executor.class)
          .assertSuccessWithOutput(EXPECTED_OUTPUT);
    }
  }

  @Test
  public void testStaticInterfaceMethodsR8() throws Exception {
    // Desugared library tests do not make sense in the Cf to Cf, and the JVM is already tested
    // in the D8 test. Just return.
    Assume.assumeFalse(parameters.isCfRuntime());
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForR8(parameters.getBackend())
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .addKeepMainRule(Executor.class)
        .addInnerClasses(StaticInterfaceMethodTest.class)
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  static class Executor {

    public static void main(String[] args) {
      System.out.println(Map.Entry.comparingByKey() == null);
      System.out.println(Chronology.getAvailableChronologies().getClass().getName());
    }
  }
}
