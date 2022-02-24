// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public abstract class BenchmarkBase extends TestBase {

  // Benchmarks must be configured with the "none" runtime as each config defines a singleton
  // benchmark in golem.
  public static List<Object[]> parametersFromConfigs(Iterable<BenchmarkConfig> configs) {
    return buildParameters(configs, getTestParameters().withNoneRuntime().build());
  }

  private final BenchmarkConfig config;

  protected BenchmarkBase(BenchmarkConfig config, TestParameters parameters) {
    this.config = config;
    parameters.assertNoneRuntime();
  }

  protected BenchmarkConfig getConfig() {
    return config;
  }

  @Test
  public void testBenchmarks() throws Exception {
    config.run(new BenchmarkEnvironment(config, temp, false));
  }

  public static BenchmarkRunner runner(BenchmarkConfig config) {
    return BenchmarkRunner.runner(config);
  }
}
