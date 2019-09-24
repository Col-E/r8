// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package j$.time;

public class TimeConversions {

  public static j$.time.ZonedDateTime from(java.time.ZonedDateTime dateTime) {
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
        from(dateTime.getZone()));
  }

  public static java.time.ZonedDateTime to(j$.time.ZonedDateTime dateTime) {
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
        to(dateTime.getZone()));
  }

  public static j$.time.ZoneId from(java.time.ZoneId zoneId) {
    if (zoneId == null) {
      return null;
    }
    return j$.time.ZoneId.of(zoneId.getId());
  }

  public static java.time.ZoneId to(j$.time.ZoneId zoneId) {
    if (zoneId == null) {
      return null;
    }
    return java.time.ZoneId.of(zoneId.getId());
  }

  // Following conversions are hidden (Used by tests APIs only).

  public static j$.time.LocalDate from(java.time.LocalDate date) {
    if (date == null) {
      return null;
    }
    return j$.time.LocalDate.of(date.getYear(), date.getMonthValue(), date.getDayOfMonth());
  }

  public static java.time.LocalDate to(j$.time.LocalDate date) {
    if (date == null) {
      return null;
    }
    return java.time.LocalDate.of(date.getYear(), date.getMonthValue(), date.getDayOfMonth());
  }

  public static j$.time.Duration from(java.time.Duration duration) {
    if (duration == null) {
      return null;
    }
    return j$.time.Duration.ofSeconds(duration.getSeconds(), duration.getNano());
  }

  public static java.time.Duration to(j$.time.Duration duration) {
    if (duration == null) {
      return null;
    }
    return java.time.Duration.ofSeconds(duration.getSeconds(), duration.getNano());
  }
}
