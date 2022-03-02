// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks;

import static java.util.Collections.emptyList;

import com.android.tools.r8.benchmarks.appdumps.TiviBenchmarks;
import com.android.tools.r8.benchmarks.desugaredlib.LegacyDesugaredLibraryBenchmark;
import com.android.tools.r8.benchmarks.helloworld.HelloWorldBenchmark;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BenchmarkCollection {

  // Actual list of all configured benchmarks.
  private final Map<String, List<BenchmarkConfig>> benchmarks = new HashMap<>();

  private void addBenchmark(BenchmarkConfig benchmark) {
    List<BenchmarkConfig> variants =
        benchmarks.computeIfAbsent(benchmark.getName(), k -> new ArrayList<>());
    for (BenchmarkConfig variant : variants) {
      BenchmarkConfig.checkBenchmarkConsistency(benchmark, variant);
    }
    variants.add(benchmark);
  }

  public BenchmarkConfig getBenchmark(BenchmarkIdentifier identifier) {
    assert identifier != null;
    List<BenchmarkConfig> configs = benchmarks.getOrDefault(identifier.getName(), emptyList());
    for (BenchmarkConfig config : configs) {
      if (identifier.equals(config.getIdentifier())) {
        return config;
      }
    }
    return null;
  }

  public static BenchmarkCollection computeCollection() {
    BenchmarkCollection collection = new BenchmarkCollection();
    // Every benchmark that should be active on golem must be setup in this method.
    HelloWorldBenchmark.configs().forEach(collection::addBenchmark);
    LegacyDesugaredLibraryBenchmark.configs().forEach(collection::addBenchmark);
    TiviBenchmarks.configs().forEach(collection::addBenchmark);
    return collection;
  }

  /** Compute and print the golem configuration. */
  public static void main(String[] args) throws IOException {
    new BenchmarkCollectionPrinter(System.out)
        .printGolemConfig(
            computeCollection().benchmarks.values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList()));
  }
}
