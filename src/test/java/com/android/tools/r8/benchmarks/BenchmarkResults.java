// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks;

import com.android.tools.r8.benchmarks.BenchmarkRunner.ResultMode;

public interface BenchmarkResults {
  // Append a runtime result. This may be summed or averaged depending on the benchmark set up.
  void addRuntimeResult(long result);

  // Append a code size result. This is always assumed to be identical if called multiple times.
  void addCodeSizeResult(long result);

  // Get the results collection for a "sub-benchmark" when defining a group of benchmarks.
  // This will throw if called on a benchmark without sub-benchmarks.
  BenchmarkResults getSubResults(String name);

  void printResults(ResultMode resultMode);

  static String prettyTime(long nanoTime) {
    return "" + (nanoTime / 1000000) + " ms";
  }

  static String prettyMetric(String name, BenchmarkMetric metric, String value) {
    return name + "(" + metric.name() + "): " + value;
  }
}
