// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks;

import com.android.tools.r8.utils.StringUtils;

public class BenchmarkRunner {

  public interface BenchmarkRunnerFunction {
    void run(BenchmarkResults results) throws Exception;
  }

  public enum ResultMode {
    AVERAGE,
    SUM;

    @Override
    public String toString() {
      return StringUtils.toLowerCase(name());
    }
  }

  private final BenchmarkConfig config;
  private int warmups = 0;
  private int iterations = 1;
  private ResultMode resultMode = ResultMode.AVERAGE;

  private BenchmarkRunner(BenchmarkConfig config) {
    this.config = config;
  }

  public static BenchmarkRunner runner(BenchmarkConfig config) {
    return new BenchmarkRunner(config);
  }

  public BenchmarkRunner setWarmupIterations(int iterations) {
    this.warmups = iterations;
    return this;
  }

  public BenchmarkRunner setBenchmarkIterations(int iterations) {
    this.iterations = iterations;
    return this;
  }

  public BenchmarkRunner reportResultAverage() {
    resultMode = ResultMode.AVERAGE;
    return this;
  }

  public BenchmarkRunner reportResultSum() {
    resultMode = ResultMode.SUM;
    return this;
  }

  public void run(BenchmarkRunnerFunction fn) throws Exception {
    long warmupTotalTime = 0;
    BenchmarkResults warmupResults = new BenchmarkResultsWarmup(config.getName());
    if (warmups > 0) {
      long start = System.nanoTime();
      for (int i = 0; i < warmups; i++) {
        fn.run(warmupResults);
      }
      warmupTotalTime = System.nanoTime() - start;
    }
    BenchmarkResults results =
        config.isSingleBenchmark()
            ? new BenchmarkResultsSingle(config.getName(), config.getMetrics())
            : new BenchmarkResultsCollection(config.getSubBenchmarks());
    long start = System.nanoTime();
    for (int i = 0; i < iterations; i++) {
      fn.run(results);
    }
    long benchmarkTotalTime = System.nanoTime() - start;
    System.out.println(
        "Benchmark results for "
            + config.getName()
            + " on target "
            + config.getTarget().getIdentifierName());
    if (warmups > 0) {
      printMetaInfo("warmup", warmups, warmupTotalTime);
      if (config.hasTimeWarmupRuns()) {
        warmupResults.printResults(resultMode);
      }
    }
    printMetaInfo("benchmark", iterations, benchmarkTotalTime);
    results.printResults(resultMode);
    System.out.println();
  }

  private void printMetaInfo(String kind, int iterations, long totalTime) {
    System.out.println("  " + kind + " reporting mode: " + resultMode);
    System.out.println("  " + kind + " iterations: " + iterations);
    System.out.println("  " + kind + " total time: " + BenchmarkResults.prettyTime(totalTime));
  }
}
