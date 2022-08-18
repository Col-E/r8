// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package timeunit;

import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

public class Example {

  public static void main(String[] args) {
    TimeUnit timeUnit = TimeUnit.of(ChronoUnit.NANOS);
    System.out.println(timeUnit.toChronoUnit());
  }
}
