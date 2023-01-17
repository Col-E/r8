// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZoneOffset;

public class LocalDateMain {

  public static void main(String[] args) {
    System.out.println(LocalDate.EPOCH);
    System.out.println(LocalDate.ofInstant(Instant.EPOCH, ZoneId.ofOffset("UTC", ZoneOffset.UTC)));
    System.out.println(LocalDate.EPOCH.toEpochSecond(LocalTime.NOON, ZoneOffset.UTC));
    System.out.println(LocalDate.EPOCH.datesUntil(LocalDate.EPOCH).count());
    System.out.println(LocalDate.EPOCH.datesUntil(LocalDate.EPOCH, Period.of(1, 1, 1)).count());
    System.out.println(
        LocalDate.EPOCH.datesUntil(LocalDate.ofEpochDay(7), Period.of(0, 0, 1)).count());
  }
}
