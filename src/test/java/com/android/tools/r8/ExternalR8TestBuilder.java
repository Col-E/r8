// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static com.android.tools.r8.ToolHelper.getJavaExecutable;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.R8Command.Builder;
import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.ToolHelper.ProcessResult;
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

  private ExternalR8TestBuilder(TestState state, Builder builder, Backend backend) {
    super(state, builder, backend);
  }

  public static ExternalR8TestBuilder create(TestState state, Backend backend) {
    return new ExternalR8TestBuilder(state, R8Command.builder(), backend);
  }

  @Override
  ExternalR8TestBuilder self() {
    return this;
  }

  @Override
  ExternalR8TestCompileResult internalCompile(
      Builder builder, Consumer<InternalOptions> optionsConsumer, Supplier<AndroidApp> app)
      throws CompilationFailedException {
    try {
      Path outputFolder = getState().getNewTempFolder();
      Path outputJar = outputFolder.resolve("output.jar");
      Path proguardMapFile = outputFolder.resolve("output.jar.map");

      List<String> command = new ArrayList<>();
      Collections.addAll(
          command,
          getJavaExecutable(),
          "-ea",
          "-cp",
          r8jar.toAbsolutePath().toString(),
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
      command.addAll(programJars.stream().map(Path::toString).collect(Collectors.toList()));

      ProcessBuilder processBuilder = new ProcessBuilder(command);
      ProcessResult processResult = ToolHelper.runProcess(processBuilder);
      assertEquals(processResult.stderr, 0, processResult.exitCode);
      String proguardMap =
          proguardMapFile.toFile().exists()
              ? FileUtils.readTextFile(proguardMapFile, Charsets.UTF_8)
              : "";
      return new ExternalR8TestCompileResult(getState(), outputJar, processResult, proguardMap);
    } catch (IOException e) {
      throw new CompilationFailedException(e);
    }
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
  public ExternalR8TestBuilder addLibraryFiles(Collection<Path> files) {
    libJars.addAll(files);
    return self();
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
}
