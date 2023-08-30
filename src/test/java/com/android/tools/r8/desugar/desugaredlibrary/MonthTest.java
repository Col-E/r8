// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK8;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.StringUtils;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MonthTest extends DesugaredLibraryTestBase {

  private static final String MISSING_STANDALONE = StringUtils.lines("4", "4", "4");
  private static final String UK_EXPECTED_RESULT = StringUtils.lines("April", "Apr", "A");

  private static final String RU_EXPECTED_RESULT_DOT = StringUtils.lines("апреля", "апр.", "А");
  private static final String RU_EXPECTED_RESULT = StringUtils.lines("апреля", "апр", "А");
  private static final String RU_EXPECTED_RESULT_DOT_NARROW_LOWERCASE =
      StringUtils.lines("апреля", "апр.", "а");

  private static final String RU_STANDALONE_EXPECTED_RESULT_ALL_UPPERCASE =
      StringUtils.lines("Апрель", "Апр.", "А");
  private static final String RU_STANDALONE_EXPECTED_RESULT_ALL_LOWERCASE =
      StringUtils.lines("апрель", "апр.", "а");
  private static final String RU_STANDALONE_EXPECTED_RESULT_NARROW_UPPERCASE =
      StringUtils.lines("апрель", "апр.", "А");
  private static final String RU_STANDALONE_EXPECTED_RESULT_LONG_NARROW_UPPERCASE =
      StringUtils.lines("Апрель", "апр.", "А");

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevels().build(),
        getJdk8Jdk11(),
        DEFAULT_SPECIFICATIONS);
  }

  public MonthTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  private String getUKStandaloneExpectedResult() {
    if (parameters.isCfRuntime() && parameters.getRuntime().asCf().isOlderThan(CfVm.JDK9)) {
      return MISSING_STANDALONE;
    }
    if (parameters.isDexRuntime()
        && libraryDesugaringSpecification.hasTimeDesugaring(parameters)
        && libraryDesugaringSpecification == JDK8) {
      return MISSING_STANDALONE;
    }
    return UK_EXPECTED_RESULT;
  }

  private String getRUExpectedResult() {
    if (parameters.isCfRuntime()) {
      if (parameters.getRuntime().asCf().isOlderThan(CfVm.JDK9)) {
        return RU_EXPECTED_RESULT;
      }
      return RU_EXPECTED_RESULT_DOT;
    }
    if (libraryDesugaringSpecification.hasTimeDesugaring(parameters)) {
      return RU_EXPECTED_RESULT_DOT_NARROW_LOWERCASE;
    }
    return RU_EXPECTED_RESULT_DOT;
  }

  private String getRUStandaloneExpectedResult() {
    if (parameters.isCfRuntime()) {
      if (parameters.getRuntime().asCf().isOlderThan(CfVm.JDK9)) {
        return RU_STANDALONE_EXPECTED_RESULT_ALL_UPPERCASE;
      }
      return RU_STANDALONE_EXPECTED_RESULT_NARROW_UPPERCASE;
    }
    if (libraryDesugaringSpecification.hasTimeDesugaring(parameters)) {
      if (libraryDesugaringSpecification == JDK8) {
        return MISSING_STANDALONE;
      }
      Version version = parameters.getDexRuntimeVersion();
      if (version.isOlderThan(Version.V4_4_4)) {
        return RU_STANDALONE_EXPECTED_RESULT_LONG_NARROW_UPPERCASE;
      }
      if (version.isOlderThan(Version.V6_0_1)) {
        return RU_STANDALONE_EXPECTED_RESULT_ALL_UPPERCASE;
      }
      return RU_STANDALONE_EXPECTED_RESULT_ALL_LOWERCASE;
    }
    return RU_STANDALONE_EXPECTED_RESULT_NARROW_UPPERCASE;
  }

  private String getExpectedResult() {
    return getUKStandaloneExpectedResult()
        + UK_EXPECTED_RESULT
        + getRUStandaloneExpectedResult()
        + getRUExpectedResult();
  }

  @Test
  public void testMonth() throws Exception {
    if (parameters.isCfRuntime()) {
      testForJvm(parameters)
          .addInnerClasses(MonthTest.class)
          .run(parameters.getRuntime(), Main.class)
          .assertSuccessWithOutput(getExpectedResult());
      return;
    }
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(getExpectedResult());
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println(Month.APRIL.getDisplayName(TextStyle.FULL_STANDALONE, Locale.UK));
      System.out.println(Month.APRIL.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.UK));
      System.out.println(Month.APRIL.getDisplayName(TextStyle.NARROW_STANDALONE, Locale.UK));

      System.out.println(Month.APRIL.getDisplayName(TextStyle.FULL, Locale.UK));
      System.out.println(Month.APRIL.getDisplayName(TextStyle.SHORT, Locale.UK));
      System.out.println(Month.APRIL.getDisplayName(TextStyle.NARROW, Locale.UK));

      Locale ru = new Locale("ru");
      System.out.println(Month.APRIL.getDisplayName(TextStyle.FULL_STANDALONE, ru));
      System.out.println(Month.APRIL.getDisplayName(TextStyle.SHORT_STANDALONE, ru));
      System.out.println(Month.APRIL.getDisplayName(TextStyle.NARROW_STANDALONE, ru));

      System.out.println(Month.APRIL.getDisplayName(TextStyle.FULL, ru));
      System.out.println(Month.APRIL.getDisplayName(TextStyle.SHORT, ru));
      System.out.println(Month.APRIL.getDisplayName(TextStyle.NARROW, ru));
    }
  }
}
