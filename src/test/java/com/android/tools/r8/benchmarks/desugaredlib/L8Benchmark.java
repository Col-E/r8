// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks.desugaredlib;

import com.android.tools.r8.L8TestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestState;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.benchmarks.BenchmarkBase;
import com.android.tools.r8.benchmarks.BenchmarkConfig;
import com.android.tools.r8.benchmarks.BenchmarkDependency;
import com.android.tools.r8.benchmarks.BenchmarkEnvironment;
import com.android.tools.r8.benchmarks.BenchmarkTarget;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Paths;
import java.util.List;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class L8Benchmark extends BenchmarkBase {

  private static final BenchmarkDependency ANDROID_JAR = BenchmarkDependency.getAndroidJar30();
  private static final BenchmarkDependency LEGACY_CONF =
      new BenchmarkDependency(
          "legacyConf",
          "2.0.3",
          Paths.get(ToolHelper.THIRD_PARTY_DIR, "openjdk", "desugar_jdk_libs_releases"));

  public L8Benchmark(BenchmarkConfig config, TestParameters parameters) {
    super(config, parameters);
  }

  @Parameters(name = "{0}")
  public static List<Object[]> data() {
    return parametersFromConfigs(configs());
  }

  public static List<BenchmarkConfig> configs() {
    return ImmutableList.of(
        BenchmarkConfig.builder()
            .setName("L8Benchmark")
            .setTarget(BenchmarkTarget.D8)
            .setFromRevision(12733)
            .setMethod(L8Benchmark::run)
            .addDependency(ANDROID_JAR)
            .addDependency(LEGACY_CONF)
            .measureRunTime()
            .build());
  }

  public static void run(BenchmarkEnvironment environment) throws Exception {
    LibraryDesugaringSpecification spec =
        new LibraryDesugaringSpecification(
            "JDK11_Benchmark",
            ImmutableSet.of(
                LEGACY_CONF.getRoot(environment).resolve("desugar_jdk_libs.jar"),
                LEGACY_CONF.getRoot(environment).resolve("desugar_jdk_libs_configuration.jar")),
            LEGACY_CONF.getRoot(environment).resolve("desugar.json"),
            ImmutableSet.of(ANDROID_JAR.getRoot(environment).resolve("android.jar")),
            LibraryDesugaringSpecification.JDK11_DESCRIPTOR,
            "");
    runner(environment.getConfig())
        .setWarmupIterations(1)
        .setBenchmarkIterations(10)
        .reportResultSum()
        .run(
            results -> {
              long start = System.nanoTime();
              L8TestBuilder.create(
                      AndroidApiLevel.B, Backend.DEX, new TestState(environment.getTemp()))
                  .apply(spec::configureL8TestBuilder)
                  .compile();
              long end = System.nanoTime();
              results.addRuntimeResult(end - start);
            });
  }
}
