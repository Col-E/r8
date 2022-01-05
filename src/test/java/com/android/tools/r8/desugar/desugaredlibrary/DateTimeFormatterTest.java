// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import com.android.tools.r8.D8TestRunResult;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
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

  private final TestParameters parameters;
  private final boolean shrinkDesugaredLibrary;
  private static final String expectedOutputDesugaredLib =
      StringUtils.lines("2/3/01 4:05 AM - Feb 3, 1 4:05 AM");
  private static final String expectedOutput =
      StringUtils.lines("2/3/01, 4:05 AM - Feb 3, 1, 4:05 AM");

  @Parameters(name = "{1}, shrinkDesugaredLibrary: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withDexRuntimes().withAllApiLevels().build());
  }

  public DateTimeFormatterTest(boolean shrinkDesugaredLibrary, TestParameters parameters) {
    this.shrinkDesugaredLibrary = shrinkDesugaredLibrary;
    this.parameters = parameters;
  }

  @Test
  public void testD8() throws Exception {
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    D8TestRunResult run =
        testForD8()
            .addLibraryFiles(getLibraryFile())
            .addInnerClasses(DateTimeFormatterTest.class)
            .setMinApi(parameters.getApiLevel())
            .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
            .setIncludeClassesChecksum(true)
            .compile()
            .addDesugaredCoreLibraryRunClassPath(
                this::buildDesugaredLibrary,
                parameters.getApiLevel(),
                keepRuleConsumer.get(),
                shrinkDesugaredLibrary)
            .run(parameters.getRuntime(), TestClass.class);
    if (requiresTimeDesugaring(parameters)) {
      run.assertSuccessWithOutput(expectedOutputDesugaredLib);
    } else {
      run.assertSuccessWithOutput(expectedOutput);
    }
  }

  @Test
  public void testR8() throws Exception {
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    R8TestRunResult run =
        testForR8(parameters.getBackend())
            .addLibraryFiles(getLibraryFile())
            .noMinification()
            .addKeepMainRule(TestClass.class)
            .addInnerClasses(DateTimeFormatterTest.class)
            .setMinApi(parameters.getApiLevel())
            .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
            .compile()
            .addDesugaredCoreLibraryRunClassPath(
                this::buildDesugaredLibrary,
                parameters.getApiLevel(),
                keepRuleConsumer.get(),
                shrinkDesugaredLibrary)
            .run(parameters.getRuntime(), TestClass.class);
    if (requiresTimeDesugaring(parameters)) {
      run.assertSuccessWithOutput(expectedOutputDesugaredLib);
    } else {
      run.assertSuccessWithOutput(expectedOutput);
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
