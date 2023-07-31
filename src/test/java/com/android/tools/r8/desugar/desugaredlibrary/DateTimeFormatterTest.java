// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;

import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.FormatStyle;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DateTimeFormatterTest extends DesugaredLibraryTestBase {

  private static final String expectedOutputDesugaredLib =
      StringUtils.lines("2/3/01 4:05 AM - Feb 3, 1 4:05 AM");
  private static final String expectedOutput =
      StringUtils.lines("2/3/01, 4:05 AM - Feb 3, 1, 4:05 AM");
  // From ICU 72, see https://android-review.git.corp.google.com/c/platform/libcore/+/2292140
  private static final String expectedOutputDesugaredLibNNBSP =
      StringUtils.lines("2/3/01 4:05\u202FAM - Feb 3, 1 4:05\u202FAM");
  private static final String expectedOutputNNBSP =
      StringUtils.lines("2/3/01, 4:05\u202FAM - Feb 3, 1, 4:05\u202FAM");

  private final TestParameters parameters;
  private final CompilationSpecification compilationSpecification;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        getJdk8Jdk11(),
        DEFAULT_SPECIFICATIONS);
  }

  public DateTimeFormatterTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.compilationSpecification = compilationSpecification;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
  }

  @Test
  public void testFormatter() throws Throwable {
    SingleTestRunResult<?> run =
        testForDesugaredLibrary(
                parameters, libraryDesugaringSpecification, compilationSpecification)
            .addInnerClasses(getClass())
            .addKeepMainRule(TestClass.class)
            .run(parameters.getRuntime(), TestClass.class)
            .assertSuccess();
    if (libraryDesugaringSpecification.hasTimeDesugaring(parameters)) {
      run.assertSuccessWithOutput(
          parameters.isDexRuntimeVersionNewerThanOrEqual(Version.V14_0_0)
                  && parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.U)
              ? expectedOutputDesugaredLibNNBSP
              : expectedOutputDesugaredLib);
    } else {
      run.assertSuccessWithOutput(
          parameters.isDexRuntimeVersionNewerThanOrEqual(Version.V14_0_0)
                  && parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.U)
              ? expectedOutputNNBSP
              : expectedOutput);
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      // See b/210885987: ANRS reported on Android 12 on the methods:
      // - j$.time.format.DateTimeFormatterBuilder.append (DateTimeFormatterBuilder.java)
      // - j$.time.format.DateTimeFormatter.<clinit> (DateTimeFormatter.java)
      // - j$.time.format.DateTimeFormatter.ofLocalizedDateTime (DateTimeFormatter.java)
      DateTimeFormatterBuilder builder = new DateTimeFormatterBuilder();
      DateTimeFormatter formatter1 = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT);
      DateTimeFormatter formatter2 =
          DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT);
      DateTimeFormatter formatter =
          builder.append(formatter1).appendLiteral(" - ").append(formatter2).toFormatter();
      LocalDateTime dateTime = LocalDateTime.of(1, 2, 3, 4, 5);
      String str = dateTime.format(formatter);
      System.out.println(str);
    }
  }
}
