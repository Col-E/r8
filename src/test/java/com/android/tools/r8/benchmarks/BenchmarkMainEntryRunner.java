// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    BenchmarkCollection collection = BenchmarkCollection.computeCollection();
    BenchmarkConfig config = collection.getBenchmark(identifier);
    if (config == null) {
      throw new RuntimeException("Unknown identifier: " + identifier);
    }

    TemporaryFolder temp = new TemporaryFolder();
    temp.create();
    try {
      // When running locally we still setup a "golem" environment and manually unpack dependencies.
      BenchmarkEnvironment environment = new BenchmarkEnvironment(config, temp, true /* isGolem */);
      if (!isGolem) {
        // When not running with golem, the python wrapper will run the benchmark in a temp
        // directory.
        // In this case the argument is the absolute path to the R8 repo.
        Path repoRoot = Paths.get(isGolemArg);
        Path dependencyDirectory = Files.createDirectories(environment.getGolemDependencyRoot());
        for (BenchmarkDependency dependency : config.getDependencies()) {
          untar(repoRoot.resolve(dependency.getTarball()), dependencyDirectory);
        }
      }
      System.out.println("Running benchmark");
      config.run(environment);
    } finally {
      temp.delete();
    }
  }

  private static void untar(Path tarball, Path target) throws IOException {
    ProcessBuilder builder =
        new ProcessBuilder("tar", "zxf", tarball.toString(), "-C", target.toString());
    ProcessResult result = ToolHelper.runProcess(builder);
    if (result.exitCode != 0) {
      throw new IOException(result.toString());
    }
  }
}
