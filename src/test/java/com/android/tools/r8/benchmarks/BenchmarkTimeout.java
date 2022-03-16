// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks;

import java.util.concurrent.TimeUnit;

public class BenchmarkTimeout {

  public final long duration;
  public final TimeUnit unit;

  public BenchmarkTimeout(long duration, TimeUnit unit) {
    this.duration = duration;
    this.unit = unit;
    if (asSeconds() == 0) {
      throw new BenchmarkConfigError("Benchmark timeout must be at least one second.");
    }
  }

  public long asSeconds() {
    return TimeUnit.SECONDS.convert(duration, unit);
  }
}
