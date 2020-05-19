// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.ToolHelper.isWindows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.FileUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.rules.TemporaryFolder;

public class KotlinCompilerTool {

  public static final class KotlinCompiler {

    private final String name;
    private final Path path;

    public KotlinCompiler(String name, Path path) {
      this.name = name;
      this.path = path;
    }

    public Path getPath() {
      return path;
    }
  }

  public static KotlinCompiler KOTLINC =
      new KotlinCompiler(
          "kotlinc",
          Paths.get(
              ToolHelper.THIRD_PARTY_DIR,
              "kotlin",
              "kotlin-compiler-1.3.72",
              "kotlinc",
              "lib",
              "kotlin-compiler.jar"));

  private final CfRuntime jdk;
  private final TestState state;
  private final KotlinCompiler compiler;
  private final KotlinTargetVersion targetVersion;
  private final List<Path> sources = new ArrayList<>();
  private final List<Path> classpath = new ArrayList<>();
  private final List<String> additionalArguments = new ArrayList<>();
  private boolean useJvmAssertions;
  private Path output = null;

  private KotlinCompilerTool(
      CfRuntime jdk, TestState state, KotlinCompiler compiler, KotlinTargetVersion targetVersion) {
    this.jdk = jdk;
    this.state = state;
    this.compiler = compiler;
    this.targetVersion = targetVersion;
  }

  public static KotlinCompilerTool create(
      CfRuntime jdk,
      TemporaryFolder temp,
      KotlinCompiler kotlinCompiler,
      KotlinTargetVersion kotlinTargetVersion) {
    return create(jdk, new TestState(temp), kotlinCompiler, kotlinTargetVersion);
  }

  public static KotlinCompilerTool create(
      CfRuntime jdk,
      TestState state,
      KotlinCompiler kotlinCompiler,
      KotlinTargetVersion kotlinTargetVersion) {
    return new KotlinCompilerTool(jdk, state, kotlinCompiler, kotlinTargetVersion);
  }

  public KotlinCompilerTool addArguments(String... arguments) {
    Collections.addAll(additionalArguments, arguments);
    return this;
  }

  public KotlinCompilerTool addSourceFiles(Path... files) {
    return addSourceFiles(Arrays.asList(files));
  }

  public KotlinCompilerTool addSourceFiles(Collection<Path> files) {
    sources.addAll(files);
    return this;
  }

  public KotlinCompilerTool addSourceFilesWithNonKtExtension(TemporaryFolder temp, Path... files) {
    return addSourceFilesWithNonKtExtension(temp, Arrays.asList(files));
  }

  public KotlinCompilerTool addSourceFilesWithNonKtExtension(
      TemporaryFolder temp, Collection<Path> files) {
    return addSourceFiles(
        files.stream()
            .map(
                fileNotNamedKt -> {
                  try {
                    // The Kotlin compiler does not require particular naming of files except for
                    // the extension, so just create a file called source.kt in a new directory.
                    Path fileNamedKt = temp.newFolder().toPath().resolve("source.kt");
                    Files.copy(fileNotNamedKt, fileNamedKt);
                    return fileNamedKt;
                  } catch (IOException e) {
                    throw new RuntimeException(e);
                  }
                })
            .collect(Collectors.toList()));
  }

  public KotlinCompilerTool addClasspathFiles(Path... files) {
    return addClasspathFiles(Arrays.asList(files));
  }

  public KotlinCompilerTool addClasspathFiles(Collection<Path> files) {
    classpath.addAll(files);
    return this;
  }

  public KotlinCompilerTool setOutputPath(Path file) {
    assertTrue("Output path must be an existing directory or a non-existing jar file",
        (!Files.exists(file) && FileUtils.isJarFile(file) && Files.exists(file.getParent()))
            || (Files.exists(file) && Files.isDirectory(file)));
    this.output = file;
    return this;
  }

  public KotlinCompilerTool setUseJvmAssertions(boolean useJvmAssertions) {
    this.useJvmAssertions = useJvmAssertions;
    return this;
  }

  private Path getOrCreateOutputPath() throws IOException {
    return output != null ? output : state.getNewTempFolder().resolve("out.jar");
  }

  /** Compile and return the compilations process result object. */
  public ProcessResult compileRaw() throws IOException {
    assertNotNull("An output path must be specified prior to compilation.", output);
    return compileInternal(output);
  }

  /** Compile asserting success and return the output path. */
  public Path compile() throws IOException {
    Path output = getOrCreateOutputPath();
    ProcessResult result = compileInternal(output);
    assertEquals(result.toString(), result.exitCode, 0);
    return output;
  }

  private ProcessResult compileInternal(Path output) throws IOException {
    List<String> cmdline = new ArrayList<>();
    cmdline.add(jdk.getJavaExecutable().toString());
    cmdline.add("-cp");
    cmdline.add(compiler.getPath().toString());
    cmdline.add(ToolHelper.K2JVMCompiler);
    if (useJvmAssertions) {
      cmdline.add("-Xassertions=jvm");
    }
    cmdline.add("-jdk-home");
    cmdline.add(jdk.getJavaHome().toString());
    cmdline.add("-jvm-target");
    cmdline.add(targetVersion.getJvmTargetString());
    for (Path source : sources) {
      cmdline.add(source.toString());
    }
    cmdline.add("-d");
    cmdline.add(output.toString());
    if (!classpath.isEmpty()) {
      cmdline.add("-cp");
      cmdline.add(classpath
          .stream()
          .map(Path::toString)
          .collect(Collectors.joining(isWindows() ? ";" : ":")));
    }
    cmdline.addAll(additionalArguments);
    ProcessBuilder builder = new ProcessBuilder(cmdline);
    return ToolHelper.runProcess(builder);
  }
}
