// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
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
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
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
  public void testStaticInterfaceMethodsR8() throws Exception {
    // Desugared library tests do not make sense in the Cf to Cf, and the JVM is already tested
    // in the D8 test. Just return.
    Assume.assumeFalse(parameters.isCfRuntime());
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForR8(parameters.getBackend())
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
