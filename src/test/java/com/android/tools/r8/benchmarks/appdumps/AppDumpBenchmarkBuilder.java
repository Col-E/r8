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
import com.android.tools.r8.benchmarks.BenchmarkTarget;
import com.android.tools.r8.dump.CompilerDump;
import com.android.tools.r8.dump.DumpOptions;
import java.io.IOException;
import java.nio.file.Path;

public class AppDumpBenchmarkBuilder {

  public static AppDumpBenchmarkBuilder builder() {
    return new AppDumpBenchmarkBuilder();
  }

  private String name;
  private BenchmarkDependency dumpDependency;
  private int fromRevision = -1;

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
            dumpDependencyPath.getFileName().toString(), dumpDependencyPath.getParent()));
  }

  public AppDumpBenchmarkBuilder setDumpDependency(BenchmarkDependency dependency) {
    this.dumpDependency = dependency;
    return this;
  }

  public AppDumpBenchmarkBuilder setFromRevision(int fromRevision) {
    this.fromRevision = fromRevision;
    return this;
  }

  public BenchmarkConfig build() {
    verify();
    return BenchmarkConfig.builder()
        .setName(name)
        .setTarget(BenchmarkTarget.R8_NON_COMPAT)
        .setMethod(run(this))
        .setFromRevision(fromRevision)
        .addDependency(dumpDependency)
        .measureRunTime()
        .measureCodeSize()
        .build();
  }

  private CompilerDump getExtractedDump(BenchmarkEnvironment environment) throws IOException {
    Path dump = dumpDependency.getRoot(environment).resolve("dump_app.zip");
    return CompilerDump.fromArchive(dump, environment.getTemp().newFolder().toPath());
  }

  private static BenchmarkMethod run(AppDumpBenchmarkBuilder builder) {
    return environment ->
        BenchmarkBase.runner(environment.getConfig())
            .setWarmupIterations(1)
            .run(
                results -> {
                  CompilerDump dump = builder.getExtractedDump(environment);
                  DumpOptions dumpProperties = dump.getBuildProperties();
                  TestBase.testForR8(environment.getTemp(), Backend.DEX)
                      // TODO(b/221811893): mock a typical setup of program providers from agp.
                      .addProgramFiles(dump.getProgramArchive())
                      .addLibraryFiles(dump.getLibraryArchive())
                      .addKeepRuleFiles(dump.getProguardConfigFile())
                      .setMinApi(dumpProperties.getMinApi())
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
}
