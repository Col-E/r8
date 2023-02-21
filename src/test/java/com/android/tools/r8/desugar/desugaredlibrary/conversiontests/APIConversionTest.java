// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.desugaredlibrary.conversiontests;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8DEBUG;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_LEGACY;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK8;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.core.StringContains.containsString;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
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
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  private static final AndroidApiLevel MIN_SUPPORTED = AndroidApiLevel.N;
  private static final String EXPECTED_RESULT =
      StringUtils.lines("[5, 6, 7]", "j$.util.stream.IntStream$VivifiedWrapper");

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getConversionParametersUpToExcluding(MIN_SUPPORTED),
        ImmutableList.of(JDK8, JDK11, JDK11_LEGACY, JDK11_PATH),
        DEFAULT_SPECIFICATIONS);
  }

  public APIConversionTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @Test
  public void testAPIConversionNoDesugaring() throws Exception {
    Assume.assumeTrue(
        "No need to test multiple times",
        compilationSpecification == D8_L8DEBUG && libraryDesugaringSpecification == JDK8);
    testForD8()
        .addInnerClasses(APIConversionTest.class)
        .setMinApi(parameters)
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
  public void testAPIConversion() throws Exception {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClasses(APIConversionTest.class)
        .addKeepMainRule(Executor.class)
        .compile()
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
