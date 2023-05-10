// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks.appdumps;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.benchmarks.BenchmarkBase;
import com.android.tools.r8.benchmarks.BenchmarkConfig;
import com.android.tools.r8.benchmarks.BenchmarkConfigError;
import com.android.tools.r8.benchmarks.BenchmarkDependency;
import com.android.tools.r8.benchmarks.BenchmarkEnvironment;
import com.android.tools.r8.benchmarks.BenchmarkMethod;
import com.android.tools.r8.benchmarks.BenchmarkMetric;
import com.android.tools.r8.benchmarks.BenchmarkSuite;
import com.android.tools.r8.benchmarks.BenchmarkTarget;
import com.android.tools.r8.dump.CompilerDump;
import com.android.tools.r8.dump.DumpOptions;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class AppDumpBenchmarkBuilder {

  public static AppDumpBenchmarkBuilder builder() {
    return new AppDumpBenchmarkBuilder();
  }

  private String name;
  private BenchmarkDependency dumpDependency;
  private int fromRevision = -1;
  private List<String> programPackages = new ArrayList<>();

  public void verify() {
    if (name == null) {
      throw new BenchmarkConfigError("Missing name");
    }
    if (dumpDependency == null) {
      throw new BenchmarkConfigError("Missing dump");
    }
    if (fromRevision < 0) {
      throw new BenchmarkConfigError("Missing from-revision");
    }
  }

  public AppDumpBenchmarkBuilder setName(String name) {
    this.name = name;
    return this;
  }

  public AppDumpBenchmarkBuilder setDumpDependencyPath(Path dumpDependencyPath) {
    return setDumpDependency(
        new BenchmarkDependency(
            "appdump",
            dumpDependencyPath.getFileName().toString(),
            dumpDependencyPath.getParent()));
  }

  public AppDumpBenchmarkBuilder setDumpDependency(BenchmarkDependency dependency) {
    this.dumpDependency = dependency;
    return this;
  }

  public AppDumpBenchmarkBuilder setFromRevision(int fromRevision) {
    this.fromRevision = fromRevision;
    return this;
  }

  public AppDumpBenchmarkBuilder addProgramPackages(String... pkgs) {
    return addProgramPackages(Arrays.asList(pkgs));
  }

  public AppDumpBenchmarkBuilder addProgramPackages(Collection<String> pkgs) {
    programPackages.addAll(pkgs);
    return this;
  }

  public BenchmarkConfig buildR8() {
    verify();
    return BenchmarkConfig.builder()
        .setName(name)
        .setTarget(BenchmarkTarget.R8_NON_COMPAT)
        .setSuite(BenchmarkSuite.OPENSOURCE_BENCHMARKS)
        .setMethod(runR8(this))
        .setFromRevision(fromRevision)
        .addDependency(dumpDependency)
        .measureRunTime()
        .measureCodeSize()
        .setTimeout(10, TimeUnit.MINUTES)
        .build();
  }

  public BenchmarkConfig buildIncrementalD8() {
    verify();
    if (programPackages.isEmpty()) {
      throw new BenchmarkConfigError(
          "Incremental benchmark should specifiy at least one program package");
    }
    return BenchmarkConfig.builder()
        .setName(name)
        .setTarget(BenchmarkTarget.D8)
        .setSuite(BenchmarkSuite.OPENSOURCE_BENCHMARKS)
        .setMethod(runIncrementalD8(this))
        .setFromRevision(fromRevision)
        .addDependency(dumpDependency)
        .addSubBenchmark(nameForLibraryPart(), BenchmarkMetric.RunTimeRaw)
        .addSubBenchmark(nameForProgramPart(), BenchmarkMetric.RunTimeRaw)
        .addSubBenchmark(nameForMergePart(), BenchmarkMetric.RunTimeRaw)
        .setTimeout(10, TimeUnit.MINUTES)
        .build();
  }

  public BenchmarkConfig buildBatchD8() {
    verify();
    return BenchmarkConfig.builder()
        .setName(name)
        .setTarget(BenchmarkTarget.D8)
        .setSuite(BenchmarkSuite.OPENSOURCE_BENCHMARKS)
        .setMethod(runBatchD8(this))
        .setFromRevision(fromRevision)
        .addDependency(dumpDependency)
        .measureRunTime()
        .measureCodeSize()
        .setTimeout(10, TimeUnit.MINUTES)
        .build();
  }

  private String nameForLibraryPart() {
    return name + "Library";
  }

  private String nameForProgramPart() {
    return name + "Program";
  }

  private String nameForMergePart() {
    return name + "Merge";
  }

  private CompilerDump getExtractedDump(BenchmarkEnvironment environment) throws IOException {
    Path dump = dumpDependency.getRoot(environment).resolve("dump_app.zip");
    return CompilerDump.fromArchive(dump, environment.getTemp().newFolder().toPath());
  }

  private static BenchmarkMethod runR8(AppDumpBenchmarkBuilder builder) {
    return environment ->
        BenchmarkBase.runner(environment.getConfig())
            .setWarmupIterations(1)
            .run(
                results -> {
                  CompilerDump dump = builder.getExtractedDump(environment);
                  DumpOptions dumpProperties = dump.getBuildProperties();
                  TestBase.testForR8(environment.getTemp(), Backend.DEX)
                      .addProgramFiles(dump.getProgramArchive())
                      .addLibraryFiles(dump.getLibraryArchive())
                      .addKeepRuleFiles(dump.getProguardConfigFile())
                      .setMinApi(dumpProperties.getMinApi())
                      .allowUnnecessaryDontWarnWildcards()
                      .allowUnusedDontWarnPatterns()
                      .allowUnusedProguardConfigurationRules()
                      // TODO(b/222228826): Disallow unrecognized diagnostics and open interfaces.
                      .allowDiagnosticMessages()
                      .addOptionsModification(
                          options ->
                              options.getOpenClosedInterfacesOptions().suppressAllOpenInterfaces())
                      .benchmarkCompile(results)
                      .benchmarkCodeSize(results);
                });
  }

  private static BenchmarkMethod runBatchD8(AppDumpBenchmarkBuilder builder) {
    return environment ->
        BenchmarkBase.runner(environment.getConfig())
            .setWarmupIterations(1)
            .run(
                results -> {
                  CompilerDump dump = builder.getExtractedDump(environment);
                  DumpOptions dumpProperties = dump.getBuildProperties();
                  TestBase.testForD8(environment.getTemp(), Backend.DEX)
                      .addProgramFiles(dump.getProgramArchive())
                      .addLibraryFiles(dump.getLibraryArchive())
                      .setMinApi(dumpProperties.getMinApi())
                      .benchmarkCompile(results)
                      .benchmarkCodeSize(results);
                });
  }

  private static BenchmarkMethod runIncrementalD8(AppDumpBenchmarkBuilder builder) {
    return environment ->
        BenchmarkBase.runner(environment.getConfig())
            .setWarmupIterations(1)
            .reportResultSum()
            .run(
                results -> {
                  CompilerDump dump = builder.getExtractedDump(environment);
                  DumpOptions dumpProperties = dump.getBuildProperties();
                  PackageSplitResources resources =
                      PackageSplitResources.create(
                          environment.getTemp(), dump.getProgramArchive(), builder.programPackages);
                  if (resources.getPackageFiles().isEmpty()) {
                    throw new RuntimeException("Unexpected empty set of program package files");
                  }

                  TestBase.testForD8(environment.getTemp(), Backend.DEX)
                      .addProgramFiles(resources.getOtherFiles())
                      .addLibraryFiles(dump.getLibraryArchive())
                      .setMinApi(dumpProperties.getMinApi())
                      .benchmarkCompile(results.getSubResults(builder.nameForLibraryPart()));

                  List<Path> programOutputs = new ArrayList<>();
                  for (Path programFile : resources.getPackageFiles()) {
                    programOutputs.add(
                        TestBase.testForD8(environment.getTemp(), Backend.DEX)
                            .addProgramFiles(programFile)
                            .addClasspathFiles(dump.getProgramArchive())
                            .addLibraryFiles(dump.getLibraryArchive())
                            .setMinApi(dumpProperties.getMinApi())
                            .setIntermediate(true)
                            .benchmarkCompile(results.getSubResults(builder.nameForProgramPart()))
                            .writeToZip());
                  }

                  TestBase.testForD8(environment.getTemp(), Backend.DEX)
                      .addProgramFiles(programOutputs)
                      .addLibraryFiles(dump.getLibraryArchive())
                      .setMinApi(dumpProperties.getMinApi())
                      .benchmarkCompile(results.getSubResults(builder.nameForMergePart()));
                });
  }
}
