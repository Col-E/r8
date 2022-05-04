// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.conversiontests;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.fail;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.CustomLibrarySpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AllTimeConversionTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  private static final AndroidApiLevel MIN_SUPPORTED = AndroidApiLevel.O;
  private static final String EXPECTED_RESULT =
      StringUtils.lines(
          "1970-01-02T00:00Z[GMT]",
          "PT0.000012345S",
          "GMT",
          "--03-02",
          "-1000000000-01-01T00:00:00.999999999Z",
          "GMT",
          "GMT");

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getConversionParametersUpToExcluding(MIN_SUPPORTED),
        getJdk8Jdk11(),
        DEFAULT_SPECIFICATIONS);
  }

  public AllTimeConversionTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @Test
  public void test() throws Exception {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addProgramClasses(Executor.class)
        .setCustomLibrarySpecification(
            new CustomLibrarySpecification(CustomLibClass.class, MIN_SUPPORTED))
        .addKeepMainRule(Executor.class)
        .addOptionsModification(options -> options.testing.trackDesugaredAPIConversions = true)
        .allowDiagnosticWarningMessages()
        .compile()
        .inspectDiagnosticMessages(this::assertTrackedAPIS)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  private void assertTrackedAPIS(TestDiagnosticMessages diagnosticMessages) {
    int trackedAPI = 0;
    int trackedCallbackAPI = 0;
    for (Diagnostic warning : diagnosticMessages.getWarnings()) {
      String message = warning.getDiagnosticMessage();
      if (message.startsWith("Tracked desugared API conversions:")) {
        trackedAPI += message.split("\n").length - 1;
      } else if (message.startsWith("Tracked callback desugared API conversions:")) {
        trackedCallbackAPI += message.split("\n").length - 1;
      } else {
        fail();
      }
    }
    assertEquals(8, trackedAPI);
    assertEquals(0, trackedCallbackAPI);
  }


  static class Executor {

    private static final String ZONE_ID = "GMT";

    public static void main(String[] args) {
      returnValueUsed();
      returnValueUnused();
      virtualMethods();
    }

    public static void returnValueUsed() {
      System.out.println(
          CustomLibClass.mix(
              ZonedDateTime.ofInstant(Instant.ofEpochSecond(0), ZoneId.of(ZONE_ID)),
              ZonedDateTime.ofInstant(Instant.ofEpochSecond(0), ZoneId.of(ZONE_ID))));
      CustomLibClass.mix(LocalDate.of(2000, 3, 13), LocalDate.of(1990, 5, 25));
      System.out.println(CustomLibClass.mix(Duration.ZERO, Duration.ofNanos(12345)));
      System.out.println(CustomLibClass.mix(ZoneId.of(ZONE_ID), ZoneId.of(ZONE_ID)));
      System.out.println(CustomLibClass.mix(MonthDay.of(3, 4), MonthDay.of(1, 2)));
      System.out.println(CustomLibClass.mix(Instant.MIN, Instant.MAX));
    }

    public static void returnValueUnused() {
      CustomLibClass.mix(
          ZonedDateTime.ofInstant(Instant.ofEpochSecond(0), ZoneId.of(ZONE_ID)),
          ZonedDateTime.ofInstant(Instant.ofEpochSecond(0), ZoneId.of(ZONE_ID)));
      CustomLibClass.mix(LocalDate.of(2000, 3, 13), LocalDate.of(1990, 5, 25));
      CustomLibClass.mix(Duration.ZERO, Duration.ofNanos(12345));
      CustomLibClass.mix(ZoneId.of(ZONE_ID), ZoneId.of(ZONE_ID));
      CustomLibClass.mix(MonthDay.of(3, 4), MonthDay.of(1, 2));
      CustomLibClass.mix(Instant.MIN, Instant.MAX);
    }

    public static void virtualMethods() {
      ZoneId of = ZoneId.of(ZONE_ID);
      CustomLibClass customLibClass = new CustomLibClass();
      customLibClass.virtual(of);
      customLibClass.virtualString(of);
      System.out.println(customLibClass.virtual(of));
      System.out.println(customLibClass.virtualString(of));
    }
  }

  // This class will be put at compilation time as library and on the runtime class path.
  // This class is convenient for easy testing. Each method plays the role of methods in the
  // platform APIs for which argument/return values need conversion.
  static class CustomLibClass {

    public static ZonedDateTime mix(ZonedDateTime zonedDateTime1, ZonedDateTime zonedDateTime2) {
      return zonedDateTime1.plusDays(zonedDateTime2.getDayOfMonth());
    }

    public static LocalDate mix(LocalDate localDate1, LocalDate localDate2) {
      return localDate1.plusDays(localDate2.getDayOfYear());
    }

    public static Duration mix(Duration duration1, Duration duration2) {
      return duration1.plus(duration2);
    }

    public static ZoneId mix(ZoneId zoneId1, ZoneId zoneId2) {
      return zoneId1;
    }

    public static MonthDay mix(MonthDay monthDay1, MonthDay monthDay2) {
      return monthDay1.withDayOfMonth(monthDay2.getDayOfMonth());
    }

    public static Instant mix(Instant instant1, Instant instant2) {
      return instant1.plusNanos(instant2.getNano());
    }

    public ZoneId virtual(ZoneId zoneId) {
      return zoneId;
    }

    public String virtualString(ZoneId zoneId) {
      return zoneId.getId();
    }
  }
}
