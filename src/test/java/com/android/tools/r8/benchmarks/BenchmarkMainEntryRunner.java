// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks;

import org.junit.rules.TemporaryFolder;

public class BenchmarkMainEntryRunner {

  public static void main(String[] args) throws Exception {
    if (args.length != 3) {
      throw new RuntimeException("Invalid arguments. Expected exactly one benchmark and target");
    }
    String benchmarkName = args[0];
    String targetIdentifier = args[1];
    String isGolemArg = args[2];
    BenchmarkIdentifier identifier = BenchmarkIdentifier.parse(benchmarkName, targetIdentifier);
    if (identifier == null) {
      throw new RuntimeException("Invalid identifier identifier: " + benchmarkName);
    }
    boolean isGolem = isGolemArg.equals("golem");
    if (!isGolem && !isGolemArg.equals("local")) {
      throw new RuntimeException(
          "Invalid value for arg 3, expected 'golem' or 'local', got '" + isGolemArg + "'");
    }
    BenchmarkCollection collection = BenchmarkCollection.computeCollection();
    BenchmarkConfig config = collection.getBenchmark(identifier);
    if (config == null) {
      throw new RuntimeException("Unknown identifier: " + identifier);
    }
    TemporaryFolder temp = new TemporaryFolder();
    temp.create();
    config.run(new BenchmarkEnvironment(config, temp, isGolem));
    temp.delete();
  }
}
