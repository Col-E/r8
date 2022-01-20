// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks;

import com.android.tools.r8.benchmarks.helloworld.HelloWorldBenchmark;
import com.android.tools.r8.errors.Unreachable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class BenchmarkCollection {

  // Actual list of all configured benchmarks.
  private final Map<BenchmarkIdentifier, BenchmarkConfig> benchmarks = new HashMap<>();

  private void addBenchmark(BenchmarkConfig benchmark) {
    BenchmarkIdentifier id = benchmark.getIdentifier();
    if (benchmarks.containsKey(id)) {
      throw new Unreachable("Duplicate definition of benchmark with name and target: " + id);
    }
    benchmarks.put(id, benchmark);
  }

  public BenchmarkConfig getBenchmark(BenchmarkIdentifier benchmark) {
    return benchmarks.get(benchmark);
  }

  public static BenchmarkCollection computeCollection() {
    BenchmarkCollection collection = new BenchmarkCollection();
    // Every benchmark that should be active on golem must be setup in this method.
    HelloWorldBenchmark.configs().forEach(collection::addBenchmark);
    return collection;
  }

  /** Compute and print the golem configuration. */
  public static void main(String[] args) throws IOException {
    new BenchmarkCollectionPrinter(System.out)
        .printGolemConfig(computeCollection().benchmarks.values());
  }
}
