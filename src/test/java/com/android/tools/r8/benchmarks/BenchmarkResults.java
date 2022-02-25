// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks;

import com.android.tools.r8.benchmarks.BenchmarkRunner.ResultMode;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;

public class BenchmarkResults {

  private final BenchmarkMetric runtimeMetric;
  private final LongList runtimeResults = new LongArrayList();
  private final LongList codeSizeResults = new LongArrayList();

  public static BenchmarkResults create() {
    return new BenchmarkResults(false);
  }

  public static BenchmarkResults createForWarmup() {
    return new BenchmarkResults(true);
  }

  private BenchmarkResults(boolean isWarmupResults) {
    this.runtimeMetric = isWarmupResults ? BenchmarkMetric.StartupTime : BenchmarkMetric.RunTimeRaw;
  }

  private boolean isWarmupResults() {
    return runtimeMetric == BenchmarkMetric.StartupTime;
  }

  private String getName(BenchmarkConfig config) {
    return config.getName();
  }

  public void addRuntimeResult(long result) {
    runtimeResults.add(result);
  }

  public void addCodeSizeResult(long result) {
    codeSizeResults.add(result);
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

  private void verifyConfigAndResults(BenchmarkConfig config) {
    verifyMetric(
        BenchmarkMetric.RunTimeRaw,
        config.getMetrics().contains(BenchmarkMetric.RunTimeRaw),
        !runtimeResults.isEmpty());
    verifyMetric(
        BenchmarkMetric.CodeSize,
        config.getMetrics().contains(BenchmarkMetric.CodeSize),
        !codeSizeResults.isEmpty());
  }

  public static String prettyTime(long nanoTime) {
    return "" + (nanoTime / 1000000) + " ms";
  }

  private void printRunTime(BenchmarkConfig config, long duration) {
    String metric = runtimeMetric.name();
    System.out.println(getName(config) + "(" + metric + "): " + prettyTime(duration));
  }

  private void printCodeSize(BenchmarkConfig config, long bytes) {
    System.out.println(getName(config) + "(CodeSize): " + bytes);
  }

  public void printResults(ResultMode mode, BenchmarkConfig config) {
    verifyConfigAndResults(config);
    if (config.hasMetric(runtimeMetric)) {
      long sum = runtimeResults.stream().mapToLong(l -> l).sum();
      if (mode == ResultMode.SUM) {
        printRunTime(config, sum);
      } else if (mode == ResultMode.AVERAGE) {
        printRunTime(config, sum / runtimeResults.size());
      }
    }
    if (!isWarmupResults() && config.hasMetric(BenchmarkMetric.CodeSize)) {
      long size = codeSizeResults.getLong(0);
      for (int i = 1; i < codeSizeResults.size(); i++) {
        if (size != codeSizeResults.getLong(i)) {
          throw new RuntimeException(
              "Unexpected code size difference: " + size + " and " + codeSizeResults.getLong(i));
        }
      }
      printCodeSize(config, size);
    }
  }
}
