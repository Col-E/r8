// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static com.android.tools.r8.ToolHelper.CLASSPATH_SEPARATOR;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.R8Command.Builder;
import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.benchmarks.BenchmarkResults;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.base.Charsets;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

// The type arguments R8Command, Builder is not relevant for running external R8.
public class ExternalR8TestBuilder
    extends TestShrinkerBuilder<
        R8Command,
        Builder,
        ExternalR8TestCompileResult,
        ExternalR8TestRunResult,
        ExternalR8TestBuilder> {

  // The r8.jar to run.
  private Path r8jar = ToolHelper.R8_JAR;

  // Ordered list of program jar entries.
  private final List<Path> programJars = new ArrayList<>();

  // Ordered list of library jar entries.
  private final List<Path> libJars = new ArrayList<>();

  // Proguard configuration file lines.
  private final List<String> config = new ArrayList<>();

  // Additional Proguard configuration files.
  private List<Path> proguardConfigFiles = new ArrayList<>();

  // External JDK to use to run R8
  private final TestRuntime runtime;

  // Enable/disable compiling with assertions
  private boolean enableAssertions = true;

  // Allow test proguard options
  private boolean allowTestProguardOptions = false;

  private String dumpInputToFile = null;
  private String dumpInputToDirectory = null;

  private boolean addR8ExternalDeps = false;

  private List<String> jvmFlags = new ArrayList<>();

  private ExternalR8TestBuilder(
      TestState state, Builder builder, Backend backend, TestRuntime runtime) {
    super(state, builder, backend);
    assert runtime != null;
    this.runtime = runtime;
  }

  public static ExternalR8TestBuilder create(
      TestState state, Backend backend, TestRuntime runtime) {
    return new ExternalR8TestBuilder(state, R8Command.builder(), backend, runtime);
  }

  @Override
  ExternalR8TestBuilder self() {
    return this;
  }

  public ExternalR8TestBuilder addJvmFlag(String flag) {
    jvmFlags.add(flag);
    return self();
  }

  public ExternalR8TestBuilder enableAssertions(boolean enable) {
    enableAssertions = enable;
    return self();
  }

  public ExternalR8TestBuilder allowTestProguardOptions(boolean allow) {
    allowTestProguardOptions = allow;
    return self();
  }

  public ExternalR8TestBuilder dumpInputToFile(String arg) {
    dumpInputToFile = arg;
    return self();
  }

  public ExternalR8TestBuilder dumpInputToDirectory(String arg) {
    dumpInputToDirectory = arg;
    return self();
  }

  @Override
  ExternalR8TestCompileResult internalCompile(
      Builder builder,
      Consumer<InternalOptions> optionsConsumer,
      Supplier<AndroidApp> app,
      BenchmarkResults benchmarkResults)
      throws CompilationFailedException {
    assert benchmarkResults == null;
    assert !libraryDesugaringTestConfiguration.isEnabled();
    try {
      Path outputFolder = getState().getNewTempFolder();
      Path outputJar = outputFolder.resolve("output.jar");
      Path proguardMapFile = outputFolder.resolve("output.jar.map");

      String classPath =
          addR8ExternalDeps
              ? r8jar.toAbsolutePath() + CLASSPATH_SEPARATOR + ToolHelper.DEPS
              : r8jar.toAbsolutePath().toString();

      List<String> command = new ArrayList<>();
      if (runtime.isDex()) {
        throw new Unimplemented();
      }
      Collections.addAll(command, runtime.asCf().getJavaExecutable().toString());

      command.addAll(jvmFlags);

      if (enableAssertions) {
        command.add("-ea");
      }

      if (allowTestProguardOptions) {
        command.add("-Dcom.android.tools.r8.allowTestProguardOptions=true");
      }

      Collections.addAll(
          command,
          "-cp",
          classPath,
          R8.class.getTypeName(),
          "--output",
          outputJar.toAbsolutePath().toString(),
          "--pg-map-output",
          proguardMapFile.toString(),
          backend == Backend.CF ? "--classfile" : "--dex",
          builder.getMode() == CompilationMode.DEBUG ? "--debug" : "--release");
      if (!config.isEmpty()) {
        Path proguardConfigFile = outputFolder.resolve("proguard-config.txt");
        FileUtils.writeTextFile(proguardConfigFile, config);
        command.add("--pg-conf");
        command.add(proguardConfigFile.toAbsolutePath().toString());
      }
      for (Path proguardConfigFile : proguardConfigFiles) {
        command.add("--pg-conf");
        command.add(proguardConfigFile.toAbsolutePath().toString());
      }
      if (libJars.isEmpty()) {
        command.add("--lib");
        command.add(TestBase.runtimeJar(backend).toAbsolutePath().toString());
      } else {
        for (Path libJar : libJars) {
          command.add("--lib");
          command.add(libJar.toAbsolutePath().toString());
        }
      }
      if (dumpInputToFile != null) {
        command.add("--dumpinputtofile");
        command.add(dumpInputToFile);
      }
      if (dumpInputToDirectory != null) {
        command.add("--dumpinputtodirectory");
        command.add(dumpInputToDirectory);
      }
      command.addAll(programJars.stream().map(Path::toString).collect(Collectors.toList()));

      ProcessBuilder processBuilder = new ProcessBuilder(command);
      ProcessResult processResult = ToolHelper.runProcess(processBuilder, getStdoutForTesting());
      assertEquals(
          "STDOUT\n:" + processResult.stdout + "\nSTDERR:\n" + processResult.stderr,
          0,
          processResult.exitCode);
      String proguardMap =
          proguardMapFile.toFile().exists()
              ? FileUtils.readTextFile(proguardMapFile, Charsets.UTF_8)
              : "";
      return new ExternalR8TestCompileResult(
          getState(), outputJar, processResult, proguardMap, getMinApiLevel(), getOutputMode());
    } catch (IOException e) {
      throw InternalCompilationFailedExceptionUtils.createForTesting(e);
    }
  }

  @Override
  public ExternalR8TestBuilder addApplyMapping(String proguardMap) {
    throw new Unimplemented("No support for adding mapfile content yet");
  }

  @Override
  public ExternalR8TestBuilder addDataEntryResources(DataEntryResource... resources) {
    throw new Unimplemented("No support for adding data entry resources");
  }

  @Override
  public ExternalR8TestBuilder addProgramClasses(Collection<Class<?>> classes) {
    // Adding a collection of classes will build a jar of exactly those classes so that no other
    // classes are made available via a too broad classpath directory.
    try {
      Path programJar = getState().getNewTempFolder().resolve("input.jar");
      ClassFileConsumer inputConsumer = new ClassFileConsumer.ArchiveConsumer(programJar);
      for (Class<?> clazz : classes) {
        String descriptor = DescriptorUtils.javaTypeToDescriptor(clazz.getName());
        inputConsumer.accept(ByteDataView.of(ToolHelper.getClassAsBytes(clazz)), descriptor, null);
      }
      inputConsumer.finished(null);
      programJars.add(programJar);
      return self();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public ExternalR8TestBuilder addProgramFiles(Collection<Path> files) {
    for (Path file : files) {
      if (FileUtils.isJarFile(file)) {
        programJars.add(file);
      } else {
        throw new Unimplemented("No support for adding paths directly");
      }
    }
    return self();
  }

  @Override
  public ExternalR8TestBuilder addProgramClassFileData(Collection<byte[]> classes) {
    throw new Unimplemented("No support for adding classfile data directly");
  }

  @Override
  public ExternalR8TestBuilder addProgramDexFileData(Collection<byte[]> data) {
    throw new Unimplemented("No support for adding dex file data directly");
  }

  @Override
  public ExternalR8TestBuilder addLibraryFiles(Collection<Path> files) {
    libJars.addAll(files);
    return self();
  }

  @Override
  public ExternalR8TestBuilder addClasspathClasses(Collection<Class<?>> classes) {
    throw new Unimplemented("No support for adding classpath data directly");
  }

  @Override
  public ExternalR8TestBuilder addClasspathFiles(Collection<Path> files) {
    throw new Unimplemented("No support for adding classpath data directly");
  }

  @Override
  public ExternalR8TestBuilder addKeepRuleFiles(List<Path> proguardConfigFiles) {
    this.proguardConfigFiles.addAll(proguardConfigFiles);
    return self();
  }

  @Override
  public ExternalR8TestBuilder addKeepRules(Collection<String> rules) {
    config.addAll(rules);
    return self();
  }

  public ExternalR8TestBuilder useR8WithRelocatedDeps() {
    return useProvidedR8(ToolHelper.R8_WITH_RELOCATED_DEPS_JAR);
  }

  public ExternalR8TestBuilder useProvidedR8(Path r8jar) {
    this.r8jar = r8jar;
    return self();
  }

  public ExternalR8TestBuilder addR8ExternalDepsToClasspath() {
    this.addR8ExternalDeps = true;
    return self();
  }
}
