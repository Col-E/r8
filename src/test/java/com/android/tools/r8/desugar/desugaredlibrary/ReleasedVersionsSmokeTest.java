// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_LEGACY;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK8;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ReleasedVersionsSmokeTest extends DesugaredLibraryTestBase {

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines(
          "true",
          "Caught java.time.format.DateTimeParseException",
          "true",
          "1970-01-02T10:17:36.789Z",
          "GMT",
          "GMT",
          "1000",
          "Hello, world");
  private static final String EXPECTED_OUTPUT_1_0_9 =
      StringUtils.lines(
          "true",
          "Caught java.time.format.DateTimeParseException",
          "true",
          "1970-01-02T10:17:36.789Z",
          "1000",
          "Hello, world");

  private final TestParameters parameters;
  private final CompilationSpecification compilationSpecification;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    ImmutableList.Builder<LibraryDesugaringSpecification> builder = ImmutableList.builder();
    builder.addAll(LibraryDesugaringSpecification.getReleased());
    builder.add(JDK8, JDK11, JDK11_LEGACY, JDK11_PATH);
    return buildParameters(
        getTestParameters().withDexRuntimes().withApiLevel(AndroidApiLevel.B).build(),
        builder.build(),
        DEFAULT_SPECIFICATIONS);
  }

  public ReleasedVersionsSmokeTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.compilationSpecification = compilationSpecification;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
  }

  @Test
  public void testSmoke() throws Throwable {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .ignoreL8FinalPrefixVerification()
        .run(parameters.getRuntime(), TestClass.class, libraryDesugaringSpecification.toString())
        .assertSuccessWithOutput(
            libraryDesugaringSpecification != LibraryDesugaringSpecification.RELEASED_1_0_9
                ? EXPECTED_OUTPUT
                : EXPECTED_OUTPUT_1_0_9);
  }

  static class TestClass {

    public static void main(String[] args) {
      String configurationVersion = args[0];
      System.out.println(Clock.systemDefaultZone().getZone().equals(ZoneId.systemDefault()));
      try {
        java.time.LocalDate.parse("");
      } catch (java.time.format.DateTimeParseException e) {
        System.out.println("Caught java.time.format.DateTimeParseException");
      }
      System.out.println(java.time.ZoneOffset.getAvailableZoneIds().size() > 0);
      System.out.println(
          java.util.Date.from(new java.util.Date(123456789).toInstant()).toInstant());

      // Support for this was added in 1.0.10.
      if (!configurationVersion.equals("RELEASED_1.0.9")) {
        java.util.TimeZone timeZone = java.util.TimeZone.getTimeZone(ZoneId.of("GMT"));
        System.out.println(timeZone.getID());
        System.out.println(timeZone.toZoneId().getId());
      }

      System.out.println(Duration.ofMillis(1000).toMillis());

      System.out.println("Hello, world");
    }
  }
}
