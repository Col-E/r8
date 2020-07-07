// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.desugaredlibrary.conversiontests;

import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.core.StringContains.containsString;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.IntUnaryOperator;
import java.util.stream.IntStream;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class APIConversionTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final boolean shrinkDesugaredLibrary;

  private static final AndroidApiLevel MIN_SUPPORTED = AndroidApiLevel.N;
  private static final String EXPECTED_RESULT =
      StringUtils.lines("[5, 6, 7]", "j$.$r8$wrapper$java$util$stream$IntStream$-V-WRP");

  @Parameters(name = "{0}, shrinkDesugaredLibrary: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getConversionParametersUpToExcluding(MIN_SUPPORTED), BooleanUtils.values());
  }

  public APIConversionTest(TestParameters parameters, boolean shrinkDesugaredLibrary) {
    this.shrinkDesugaredLibrary = shrinkDesugaredLibrary;
    this.parameters = parameters;
  }

  @Test
  public void testAPIConversionNoDesugaring() throws Exception {
    Assume.assumeTrue("No need to test twice", shrinkDesugaredLibrary);
    testForD8()
        .addInnerClasses(APIConversionTest.class)
        .setMinApi(parameters.getApiLevel())
        .compile()
        .assertNoWarningMessageThatMatches(containsString("java.util.Arrays#setAll"))
        .assertNoWarningMessageThatMatches(containsString("java.util.Random#ints"))
        .assertNoWarningMessageThatMatches(endsWith("is a desugared type)."))
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutput(
            StringUtils.lines(
                "[5, 6, 7]", "java.util.stream.IntPipeline$Head", "IntSummaryStatistics"));
  }

  @Test
  public void testAPIConversionDesugaringD8() throws Exception {
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForD8()
        .addInnerClasses(APIConversionTest.class)
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .assertNoMessages()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .run(parameters.getRuntime(), Executor.class)
        .assertFailureWithOutput(EXPECTED_RESULT)
        .assertFailureWithErrorThatMatches(
            containsString(
                "Java 8+ API desugaring (library desugaring) cannot convert"
                    + " from java.util.IntSummaryStatistics"));
  }

  @Test
  public void testAPIConversionDesugaringR8() throws Exception {
    Assume.assumeTrue("Invalid runtime library (missing applyAsInt)", false);
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForR8(parameters.getBackend())
        .addInnerClasses(APIConversionTest.class)
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(Executor.class)
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .assertNoMessages()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .run(parameters.getRuntime(), Executor.class)
        .assertFailureWithOutput(EXPECTED_RESULT)
        .assertFailureWithErrorThatMatches(
            containsString(
                "Java 8+ API desugaring (library desugaring) cannot convert"
                    + " from java.util.IntSummaryStatistics"));
  }

  static class Executor {

    public static void main(String[] args) {
      int[] ints = new int[3];
      Arrays.setAll(ints, new MyFunction());
      System.out.println(Arrays.toString(ints));
      IntStream intStream = new Random().ints();
      System.out.println(intStream.getClass().getName());
      CharSequence charSequence =
          new CharSequence() {
            @Override
            public int length() {
              return 1;
            }

            @Override
            public char charAt(int index) {
              return 42;
            }

            @Override
            public CharSequence subSequence(int start, int end) {
              return null;
            }
          };
      IntStream fixedSizedIntStream = charSequence.codePoints();
      try {
        System.out.println(fixedSizedIntStream.summaryStatistics().getClass().getSimpleName());
      } catch (RuntimeException e) {
        System.out.println(e.getMessage());
      }
    }
  }

  static class MyFunction implements IntUnaryOperator {

    @Override
    public int applyAsInt(int operand) {
      return operand + 5;
    }
  }
}
