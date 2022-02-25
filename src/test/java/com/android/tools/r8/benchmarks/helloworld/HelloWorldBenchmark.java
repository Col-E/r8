// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks.helloworld;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.benchmarks.BenchmarkBase;
import com.android.tools.r8.benchmarks.BenchmarkConfig;
import com.android.tools.r8.benchmarks.BenchmarkDependency;
import com.android.tools.r8.benchmarks.BenchmarkEnvironment;
import com.android.tools.r8.benchmarks.BenchmarkMethod;
import com.android.tools.r8.benchmarks.BenchmarkTarget;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** Example of setting up a benchmark based on the testing infrastructure. */
@RunWith(Parameterized.class)
public class HelloWorldBenchmark extends BenchmarkBase {

  @Parameters(name = "{0}")
  public static List<Object[]> data() {
    return parametersFromConfigs(configs());
  }

  public HelloWorldBenchmark(BenchmarkConfig config, TestParameters parameters) {
    super(config, parameters);
  }

  /** Static method to add benchmarks to the benchmark collection. */
  public static List<BenchmarkConfig> configs() {
    Builder<BenchmarkConfig> benchmarks = ImmutableList.builder();
    makeBenchmark(BenchmarkTarget.D8, HelloWorldBenchmark::benchmarkD8, benchmarks);
    makeBenchmark(BenchmarkTarget.R8_NON_COMPAT, HelloWorldBenchmark::benchmarkR8, benchmarks);
    return benchmarks.build();
  }

  // Options/parameter setup to define variants of the benchmark above.
  // Other benchmarks may not need this kind of options. It is just to help create the variants.
  private static class Options {
    final BenchmarkTarget target;
    final Backend backend;
    final BenchmarkDependency library;
    final AndroidApiLevel minApi = AndroidApiLevel.B;

    public Options(BenchmarkTarget target, Backend backend, boolean includeLibrary) {
      this.target = target;
      this.backend = backend;
      library = includeLibrary ? BenchmarkDependency.getRuntimeJarJava8() : null;
    }

    public String getName() {
      // The name include each non-target option for the variants to ensure unique benchmarks.
      String backendString = backend.isCf() ? "Cf" : "Dex";
      String libraryString = library != null ? "" : "NoLib";
      return "HelloWorld" + backendString + libraryString;
    }
  }

  private static void makeBenchmark(
      BenchmarkTarget target,
      Function<Options, BenchmarkMethod> method,
      ImmutableList.Builder<BenchmarkConfig> benchmarks) {
    for (boolean includeLibrary : BooleanUtils.values()) {
      for (Backend backend : Backend.values()) {
        Options options = new Options(target, backend, includeLibrary);
        BenchmarkConfig.Builder builder =
            BenchmarkConfig.builder()
                // The benchmark is required to have a unique combination of name and target.
                .setName(options.getName())
                .setTarget(target)
                // The benchmark is required to have at least one metric.
                .measureRunTime()
                .measureCodeSize()
                // The benchmark is required to have a runner method which defines the actual
                // execution.
                .setMethod(method.apply(options))
                // The benchmark is required to set a "golem from revision".
                // Find this value by looking at the current revision on golem.
                .setFromRevision(12150)
                // The benchmark can optionally time the warmup. This is not needed to use a warmup
                // in the actual run, only to include it as its own benchmark entry on golem.
                .measureWarmup();
        // If compiling with a library it needs to be added as a dependency.
        if (options.library != null) {
          builder.addDependency(options.library);
        }
        benchmarks.add(builder.build());
      }
    }
  }

  public static BenchmarkMethod benchmarkD8(Options options) {
    return environment ->
        runner(environment.getConfig())
            .setWarmupIterations(1)
            .setBenchmarkIterations(100)
            .reportResultSum()
            .run(
                results ->
                    testForD8(environment.getTemp(), options.backend)
                        .setMinApi(options.minApi)
                        .addLibraryFiles(getLibraryFiles(options, environment))
                        .addProgramClasses(TestClass.class)
                        // Compile and measure the run time.
                        .benchmarkCompile(results)
                        // Measure the output size.
                        .benchmarkCodeSize(results));
  }

  public static BenchmarkMethod benchmarkR8(Options options) {
    return environment ->
        runner(environment.getConfig())
            .setWarmupIterations(1)
            .setBenchmarkIterations(4)
            .reportResultSum()
            .run(
                results ->
                    testForR8(environment.getTemp(), options.backend)
                        .addLibraryFiles(getLibraryFiles(options, environment))
                        .applyIf(options.library == null, b -> b.addDontWarn("*"))
                        .setMinApi(options.minApi)
                        .addProgramClasses(TestClass.class)
                        .addKeepMainRule(TestClass.class)
                        // Compile and measure the run time.
                        .benchmarkCompile(results)
                        // Measure the output size.
                        .benchmarkCodeSize(results));
  }

  private static Collection<Path> getLibraryFiles(
      Options options, BenchmarkEnvironment environment) {
    return options.library != null
        ? Collections.singletonList(options.library.getRoot(environment).resolve("rt.jar"))
        : Collections.emptyList();
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println("Hello world!");
    }
  }
}
