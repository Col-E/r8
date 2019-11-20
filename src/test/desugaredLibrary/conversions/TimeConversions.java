// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package java.time;

public class TimeConversions {

  private TimeConversions() {}

  public static j$.time.ZonedDateTime convert(java.time.ZonedDateTime dateTime) {
    if (dateTime == null) {
      return null;
    }
    return j$.time.ZonedDateTime.of(
        dateTime.getYear(),
        dateTime.getMonthValue(),
        dateTime.getDayOfMonth(),
        dateTime.getHour(),
        dateTime.getMinute(),
        dateTime.getSecond(),
        dateTime.getNano(),
        convert(dateTime.getZone()));
  }

  public static java.time.ZonedDateTime convert(j$.time.ZonedDateTime dateTime) {
    if (dateTime == null) {
      return null;
    }
    return java.time.ZonedDateTime.of(
        dateTime.getYear(),
        dateTime.getMonthValue(),
        dateTime.getDayOfMonth(),
        dateTime.getHour(),
        dateTime.getMinute(),
        dateTime.getSecond(),
        dateTime.getNano(),
        convert(dateTime.getZone()));
  }

  // ZoneId conversion works because in practice only two final classes are used.
  // ZoneId is responsible for using one of the other final classes.
  // This does not support custom implementations of ZoneId.

  public static j$.time.ZoneId convert(java.time.ZoneId zoneId) {
    if (zoneId == null) {
      return null;
    }
    return j$.time.ZoneId.of(zoneId.getId());
  }

  public static java.time.ZoneId convert(j$.time.ZoneId zoneId) {
    if (zoneId == null) {
      return null;
    }
    return java.time.ZoneId.of(zoneId.getId());
  }

  public static j$.time.MonthDay convert(java.time.MonthDay monthDay) {
    if (monthDay == null) {
      return null;
    }
    return j$.time.MonthDay.of(monthDay.getMonthValue(), monthDay.getDayOfMonth());
  }

  public static java.time.MonthDay convert(j$.time.MonthDay monthDay) {
    if (monthDay == null) {
      return null;
    }
    return java.time.MonthDay.of(monthDay.getMonthValue(), monthDay.getDayOfMonth());
  }

  public static j$.time.Instant convert(java.time.Instant instant) {
    if (instant == null) {
      return null;
    }
    return j$.time.Instant.ofEpochSecond(instant.getEpochSecond(), (long) instant.getNano());
  }

  public static java.time.Instant convert(j$.time.Instant instant) {
    if (instant == null) {
      return null;
    }
    return java.time.Instant.ofEpochSecond(instant.getEpochSecond(), (long) instant.getNano());
  }

  // Following conversions are hidden (Used by tests APIs only).

  public static j$.time.LocalDate convert(java.time.LocalDate date) {
    if (date == null) {
      return null;
    }
    return j$.time.LocalDate.of(date.getYear(), date.getMonthValue(), date.getDayOfMonth());
  }

  public static java.time.LocalDate convert(j$.time.LocalDate date) {
    if (date == null) {
      return null;
    }
    return java.time.LocalDate.of(date.getYear(), date.getMonthValue(), date.getDayOfMonth());
  }

  public static j$.time.Duration convert(java.time.Duration duration) {
    if (duration == null) {
      return null;
    }
    return j$.time.Duration.ofSeconds(duration.getSeconds(), duration.getNano());
  }

  public static java.time.Duration convert(j$.time.Duration duration) {
    if (duration == null) {
      return null;
    }
    return java.time.Duration.ofSeconds(duration.getSeconds(), duration.getNano());
  }
}
