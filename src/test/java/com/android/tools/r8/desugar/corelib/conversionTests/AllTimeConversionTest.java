// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.corelib.conversionTests;

import com.android.tools.r8.TestRuntime.DexRuntime;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.Test;

public class AllTimeConversionTest extends APIConversionTestBase {

  @Test
  public void testRewrittenAPICalls() throws Exception {
    Path customLib = testForD8().addProgramClasses(CustomLibClass.class).compile().writeToZip();
    testForD8()
        .setMinApi(AndroidApiLevel.B)
        .addProgramClasses(Executor.class)
        .addLibraryClasses(CustomLibClass.class)
        .enableCoreLibraryDesugaring(AndroidApiLevel.B)
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibraryWithConversionExtension, AndroidApiLevel.B)
        .addRunClasspathFiles(customLib)
        .run(new DexRuntime(DexVm.ART_9_0_0_HOST), Executor.class)
        .assertSuccessWithOutput(
            StringUtils.lines(
                "1970-01-02T00:00Z[GMT]",
                "PT0.000012345S",
                "GMT",
                "--03-02",
                "-1000000000-01-01T00:00:00.999999999Z",
                "GMT",
                "GMT"));
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
