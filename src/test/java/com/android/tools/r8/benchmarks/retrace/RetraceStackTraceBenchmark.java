// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks.retrace;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.benchmarks.BenchmarkBase;
import com.android.tools.r8.benchmarks.BenchmarkConfig;
import com.android.tools.r8.benchmarks.BenchmarkDependency;
import com.android.tools.r8.benchmarks.BenchmarkMethod;
import com.android.tools.r8.benchmarks.BenchmarkTarget;
import com.android.tools.r8.retrace.ProguardMapProducer;
import com.android.tools.r8.retrace.ProguardMappingSupplier;
import com.android.tools.r8.retrace.Retrace;
import com.android.tools.r8.retrace.RetraceCommand;
import com.google.common.collect.ImmutableList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** Example of setting up a benchmark based on the testing infrastructure. */
@RunWith(Parameterized.class)
public class RetraceStackTraceBenchmark extends BenchmarkBase {

  private static final BenchmarkDependency benchmarkDependency =
      new BenchmarkDependency(
          "retraceBenchmark", "retrace_benchmark", Paths.get(ToolHelper.THIRD_PARTY_DIR));

  @Parameters(name = "{0}")
  public static List<Object[]> data() {
    return parametersFromConfigs(configs());
  }

  public RetraceStackTraceBenchmark(BenchmarkConfig config, TestParameters parameters) {
    super(config, parameters);
  }

  /** Static method to add benchmarks to the benchmark collection. */
  public static List<BenchmarkConfig> configs() {
    return ImmutableList.<BenchmarkConfig>builder()
        .add(
            BenchmarkConfig.builder()
                .setName("RetraceStackTraceWithProguardMap")
                .setTarget(BenchmarkTarget.R8_NON_COMPAT)
                .measureRunTime()
                .setMethod(benchmarkRetrace())
                .setFromRevision(12266)
                .measureWarmup()
                .addDependency(benchmarkDependency)
                .build())
        .build();
  }

  public static BenchmarkMethod benchmarkRetrace() {
    return environment ->
        runner(environment.getConfig())
            .setWarmupIterations(1)
            .setBenchmarkIterations(4)
            .reportResultSum()
            .run(
                results -> {
                  Path dependencyRoot = benchmarkDependency.getRoot(environment);
                  List<String> stackTrace =
                      Files.readAllLines(dependencyRoot.resolve("stacktrace.txt"));
                  List<String> retraced = new ArrayList<>();
                  long start = System.nanoTime();
                  Retrace.run(
                      RetraceCommand.builder()
                          .setMappingSupplier(
                              ProguardMappingSupplier.builder()
                                  .setProguardMapProducer(
                                      ProguardMapProducer.fromPath(
                                          dependencyRoot.resolve("r8lib.jar.map")))
                                  .setLoadAllDefinitions(false)
                                  .build())
                          .setStackTrace(stackTrace)
                          .setRetracedStackTraceConsumer(retraced::addAll)
                          .build());
                  long end = System.nanoTime();
                  // Add a simple check to ensure that we do not, in case of invalid retracing,
                  // record an optimal benchmark result.
                  if (retraced.size() < stackTrace.size()) {
                    throw new RuntimeException("Unexpected missing lines in retraced result");
                  }
                  results.addRuntimeResult(end - start);
                });
  }
}
