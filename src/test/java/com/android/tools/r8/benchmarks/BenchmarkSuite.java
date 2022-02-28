// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks;

/** Enumeration of the benchmark suites on Golem. */
public enum BenchmarkSuite {
  R8_BENCHMARKS("R8Benchmarks", "suite"),
  OPENSOURCE_BENCHMARKS("OpenSourceAppDumps", "dumpsSuite");

  private final String golemName;
  private final String dartName;

  public static BenchmarkSuite getDefault() {
    return R8_BENCHMARKS;
  }

  BenchmarkSuite(String golemName, String dartName) {
    this.golemName = golemName;
    this.dartName = dartName;
  }

  /** The name as shown in golems listings. */
  public String getGolemName() {
    return golemName;
  }

  /** The variable name used for the suite in the benchmarks.dart script. */
  public String getDartName() {
    return dartName;
  }
}
