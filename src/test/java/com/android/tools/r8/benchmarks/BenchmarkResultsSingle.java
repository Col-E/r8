// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks;

import com.android.tools.r8.benchmarks.BenchmarkRunner.ResultMode;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import java.util.Set;

public class BenchmarkResultsSingle implements BenchmarkResults {

  private String name;
  private final Set<BenchmarkMetric> metrics;
  private final LongList runtimeResults = new LongArrayList();
  private final LongList codeSizeResults = new LongArrayList();

  public BenchmarkResultsSingle(String name, Set<BenchmarkMetric> metrics) {
    this.name = name;
    this.metrics = metrics;
  }

  @Override
  public void addRuntimeResult(long result) {
    verifyMetric(BenchmarkMetric.RunTimeRaw, metrics.contains(BenchmarkMetric.RunTimeRaw), true);
    runtimeResults.add(result);
  }

  @Override
  public void addCodeSizeResult(long result) {
    verifyMetric(BenchmarkMetric.CodeSize, metrics.contains(BenchmarkMetric.CodeSize), true);
    codeSizeResults.add(result);
  }

  @Override
  public BenchmarkResults getSubResults(String name) {
    throw new BenchmarkConfigError(
        "Unexpected attempt to get sub-results for benchmark without sub-benchmarks");
  }

  private static void verifyMetric(BenchmarkMetric metric, boolean expected, boolean actual) {
    if (expected != actual) {
      throw new BenchmarkConfigError(
          "Mismatched config and result for "
              + metric.name()
              + ". Expected by config: "
              + expected
              + ", but has result: "
              + actual);
    }
  }

  private void verifyConfigAndResults() {
    verifyMetric(
        BenchmarkMetric.RunTimeRaw,
        metrics.contains(BenchmarkMetric.RunTimeRaw),
        !runtimeResults.isEmpty());
    verifyMetric(
        BenchmarkMetric.CodeSize,
        metrics.contains(BenchmarkMetric.CodeSize),
        !codeSizeResults.isEmpty());
  }

  private void printRunTime(long duration) {
    String value = BenchmarkResults.prettyTime(duration);
    System.out.println(BenchmarkResults.prettyMetric(name, BenchmarkMetric.RunTimeRaw, value));
  }

  private void printCodeSize(long bytes) {
    System.out.println(BenchmarkResults.prettyMetric(name, BenchmarkMetric.CodeSize, "" + bytes));
  }

  @Override
  public void printResults(ResultMode mode) {
    verifyConfigAndResults();
    if (!runtimeResults.isEmpty()) {
      long sum = runtimeResults.stream().mapToLong(l -> l).sum();
      long result = mode == ResultMode.SUM ? sum : sum / runtimeResults.size();
      printRunTime(result);
    }
    if (!codeSizeResults.isEmpty()) {
      long size = codeSizeResults.getLong(0);
      for (int i = 1; i < codeSizeResults.size(); i++) {
        if (size != codeSizeResults.getLong(i)) {
          throw new RuntimeException(
              "Unexpected code size difference: " + size + " and " + codeSizeResults.getLong(i));
        }
      }
      printCodeSize(size);
    }
  }
}
