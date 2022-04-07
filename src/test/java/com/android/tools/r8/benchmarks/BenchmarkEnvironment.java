// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.rules.TemporaryFolder;

public class BenchmarkEnvironment {

  private final BenchmarkConfig config;
  private final TemporaryFolder temp;
  private final boolean isGolem;

  public BenchmarkEnvironment(BenchmarkConfig config, TemporaryFolder temp, boolean isGolem) {
    this.config = config;
    this.temp = temp;
    this.isGolem = isGolem;
  }

  public BenchmarkConfig getConfig() {
    return config;
  }

  public TemporaryFolder getTemp() {
    return temp;
  }

  public Path translateDependencyPath(String directoryName, Path location) {
    return isGolem
        ? getGolemDependencyRoot().resolve(directoryName)
        : location.resolve(directoryName);
  }

  public Path getGolemDependencyRoot() {
    return Paths.get("benchmarks", config.getDependencyDirectoryName());
  }
}
