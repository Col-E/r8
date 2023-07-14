// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks.desugaredlib;

import com.android.tools.r8.StringResource;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.benchmarks.BenchmarkBase;
import com.android.tools.r8.benchmarks.BenchmarkConfig;
import com.android.tools.r8.benchmarks.BenchmarkDependency;
import com.android.tools.r8.benchmarks.BenchmarkEnvironment;
import com.android.tools.r8.benchmarks.BenchmarkTarget;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LegacyDesugaredLibraryBenchmark extends BenchmarkBase {

  private static final BenchmarkDependency androidJar = BenchmarkDependency.getAndroidJar30();
  private static final BenchmarkDependency legacyConf =
      new BenchmarkDependency(
          "legacyConf",
          "desugar_jdk_libs_legacy",
          Paths.get(ToolHelper.THIRD_PARTY_DIR, "openjdk"));

  public LegacyDesugaredLibraryBenchmark(BenchmarkConfig config, TestParameters parameters) {
    super(config, parameters);
  }

  @Parameters(name = "{0}")
  public static List<Object[]> data() {
    return parametersFromConfigs(configs());
  }

  public static List<BenchmarkConfig> configs() {
    return ImmutableList.of(
        BenchmarkConfig.builder()
            .setName("LegacyDesugaredLibraryConf")
            .setTarget(BenchmarkTarget.D8)
            .setFromRevision(12150)
            .setMethod(LegacyDesugaredLibraryBenchmark::run)
            .addDependency(androidJar)
            .addDependency(legacyConf)
            .measureRunTime()
            .build());
  }

  public static void run(BenchmarkEnvironment environment) throws Exception {
    runner(environment.getConfig())
        .setWarmupIterations(1)
        .setBenchmarkIterations(10)
        .reportResultSum()
        .run(
            results ->
                testForD8(environment.getTemp(), Backend.DEX)
                    .setMinApi(AndroidApiLevel.B)
                    .addLibraryFiles(androidJar.getRoot(environment).resolve("android.jar"))
                    .apply(
                        b ->
                            b.getBuilder()
                                .addDesugaredLibraryConfiguration(
                                    StringResource.fromFile(
                                        legacyConf
                                            .getRoot(environment)
                                            .resolve("desugar_jdk_libs.json"))))
                    .benchmarkCompile(results));
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(Stream.of("Hello", "world!").collect(Collectors.joining(" ")));
    }
  }
}
