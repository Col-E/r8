// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.desugaredlibrary.conversiontests;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SuperAPIConversionTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final boolean shrinkDesugaredLibrary;

  private static final AndroidApiLevel MIN_SUPPORTED = AndroidApiLevel.N;

  @Parameters(name = "{0}, shrinkDesugaredLibrary: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getConversionParametersUpToExcluding(MIN_SUPPORTED), BooleanUtils.values());
  }

  public SuperAPIConversionTest(TestParameters parameters, boolean shrinkDesugaredLibrary) {
    this.shrinkDesugaredLibrary = shrinkDesugaredLibrary;
    this.parameters = parameters;
  }

  @Test
  public void testAPIConversionNoDesugaring() throws Exception {
    Assume.assumeTrue("No need to test twice", shrinkDesugaredLibrary);
    testForD8()
        .addInnerClasses(SuperAPIConversionTest.class)
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutputLines("Head");
  }

  @Test
  public void testAPIConversionDesugaringD8() throws Exception {
    Assume.assumeFalse("TODO(b/189435770): fix", shrinkDesugaredLibrary);
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForD8()
        .addInnerClasses(SuperAPIConversionTest.class)
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutputLines("$r8$wrapper$java$util$stream$IntStream$-V-WRP");
  }

  @Test
  public void testAPIConversionDesugaringR8() throws Exception {
    Assume.assumeFalse("TODO(b/189435770): fix", shrinkDesugaredLibrary);
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForR8(parameters.getBackend())
        .addInnerClasses(SuperAPIConversionTest.class)
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(Executor.class)
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutputLines("$r8$wrapper$java$util$stream$IntStream$-V-WRP");
  }

  static class ParallelRandom extends Random {

    @Override
    public IntStream ints() {
      return super.ints().parallel();
    }
  }

  static class Executor {

    public static void main(String[] args) {
      IntStream intStream = new ParallelRandom().ints();
      System.out.println(intStream.getClass().getSimpleName());
    }
  }
}
