// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime;
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

  private static final String EXPECTED_JAVA_8_OUTPUT = StringUtils.lines("4");
  private static final String EXPECTED_JAVA_9_OUTPUT = StringUtils.lines("April");

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

  private String getExpectedResult(TestParameters parameters) {
    if (parameters.isCfRuntime()) {
      if (parameters.getRuntime().asCf().isNewerThan(TestRuntime.CfVm.JDK8)) {
        return EXPECTED_JAVA_9_OUTPUT;
      }
      return EXPECTED_JAVA_8_OUTPUT;
    }
    assert parameters.isDexRuntime();
    if (libraryDesugaringSpecification.hasTimeDesugaring(parameters)) {
      return EXPECTED_JAVA_8_OUTPUT;
    }
    return EXPECTED_JAVA_9_OUTPUT;
  }

  @Test
  public void testMonth() throws Exception {
    if (parameters.isCfRuntime()) {
      testForJvm(parameters)
          .addInnerClasses(MonthTest.class)
          .run(parameters.getRuntime(), MonthTest.Main.class)
          .assertSuccessWithOutput(getExpectedResult(parameters));
      return;
    }
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(getExpectedResult(parameters));
  }

  static class Main {
    public static void main(String[] args) {
      System.out.println(Month.APRIL.getDisplayName(TextStyle.FULL_STANDALONE, Locale.UK));
    }
  }
}
