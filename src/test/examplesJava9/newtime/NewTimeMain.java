// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package newtime;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

public class NewTimeMain {

  public static void main(String[] args) {
    Clock utc = Clock.tickMillis(ZoneId.of("UTC"));
    System.out.println(utc.getZone());
    OffsetTime ot = OffsetTime.of(LocalTime.NOON, ZoneOffset.UTC);
    System.out.println(ot.toEpochSecond(LocalDate.MIN));
  }
}
