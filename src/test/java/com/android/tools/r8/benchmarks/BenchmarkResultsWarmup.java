// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks;

import com.android.tools.r8.benchmarks.BenchmarkRunner.ResultMode;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;

public class BenchmarkResultsWarmup implements BenchmarkResults {

  private final String name;
  private final LongList runtimeResults = new LongArrayList();
  private long codeSizeResult = -1;

  public BenchmarkResultsWarmup(String name) {
    this.name = name;
  }

  @Override
  public void addRuntimeResult(long result) {
    runtimeResults.add(result);
  }

  @Override
  public void addCodeSizeResult(long result) {
    if (codeSizeResult == -1) {
      codeSizeResult = result;
    }
    if (codeSizeResult != result) {
      throw new RuntimeException(
          "Unexpected code size difference: " + result + " and " + codeSizeResult);
    }
  }

  @Override
  public BenchmarkResults getSubResults(String name) {
    // When running warmups all results are amended to the single warmup result.
    return this;
  }

  @Override
  public void printResults(ResultMode mode) {
    if (runtimeResults.isEmpty()) {
      throw new BenchmarkConfigError("Expected runtime results for warmup run");
    }
    long sum = runtimeResults.stream().mapToLong(l -> l).sum();
    long result = mode == ResultMode.SUM ? sum : sum / runtimeResults.size();
    System.out.println(
        BenchmarkResults.prettyMetric(
            name, BenchmarkMetric.StartupTime, BenchmarkResults.prettyTime(result)));
  }
}
