// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks;

import com.android.tools.r8.benchmarks.BenchmarkRunner.ResultMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BenchmarkResultsCollection implements BenchmarkResults {

  private final Map<String, BenchmarkResultsSingle> results;

  public BenchmarkResultsCollection(Map<String, Set<BenchmarkMetric>> benchmarks) {
    results = new HashMap<>(benchmarks.size());
    benchmarks.forEach(
        (name, metrics) -> results.put(name, new BenchmarkResultsSingle(name, metrics)));
  }

  public void addRuntimeResult(long result) {
    throw new BenchmarkConfigError(
        "Unexpected attempt to add a runtime result to a the root of a benchmark with"
            + " sub-benchmarks");
  }

  public void addCodeSizeResult(long result) {
    throw new BenchmarkConfigError(
        "Unexpected attempt to add a runtime result to a the root of a benchmark with"
            + " sub-benchmarks");
  }

  @Override
  public BenchmarkResults getSubResults(String name) {
    return results.get(name);
  }

  @Override
  public void printResults(ResultMode mode) {
    List<String> sorted = new ArrayList<>(results.keySet());
    sorted.sort(String::compareTo);
    for (String name : sorted) {
      BenchmarkResultsSingle singleResults = results.get(name);
      singleResults.printResults(mode);
    }
  }
}
