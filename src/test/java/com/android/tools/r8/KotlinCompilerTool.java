// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.ToolHelper.KT_COMPILER;
import static com.android.tools.r8.ToolHelper.KT_PRELOADER;
import static com.android.tools.r8.ToolHelper.isWindows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.FileUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.rules.TemporaryFolder;

public class KotlinCompilerTool {

  private final CfRuntime jdk;
  private final TestState state;
  private final String kotlincJar;
  private final List<Path> sources = new ArrayList<>();
  private final List<Path> classpath = new ArrayList<>();
  private Path output = null;

  private KotlinCompilerTool(CfRuntime jdk, TestState state, Path kotlincJar) {
    this.jdk = jdk;
    this.state = state;
    this.kotlincJar = kotlincJar == null ? KT_COMPILER : kotlincJar.toString();
  }

  public static KotlinCompilerTool create(CfRuntime jdk, TemporaryFolder temp) {
    return create(jdk, new TestState(temp), null);
  }

  public static KotlinCompilerTool create (CfRuntime jdk, TemporaryFolder temp, Path kotlincJar) {
    return create(jdk, new TestState(temp), kotlincJar);
  }

  public static KotlinCompilerTool create(CfRuntime jdk, TestState state, Path kotlincJar) {
    return new KotlinCompilerTool(jdk, state, kotlincJar);
  }

  public KotlinCompilerTool addSourceFiles(Path files) {
    return addSourceFiles(Arrays.asList(files));
  }

  public KotlinCompilerTool addSourceFiles(Collection<Path> files) {
    sources.addAll(files);
    return this;
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

  private Path getOrCreateOutputPath() throws IOException {
    return output != null
        ? output
        : state.getNewTempFolder().resolve("out.jar");
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
    cmdline.add("-jar");
    cmdline.add(KT_PRELOADER);
    cmdline.add("org.jetbrains.kotlin.preloading.Preloader");
    cmdline.add("-cp");
    cmdline.add(kotlincJar);
    cmdline.add(ToolHelper.K2JVMCompiler);
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
    ProcessBuilder builder = new ProcessBuilder(cmdline);
    return ToolHelper.runProcess(builder);
  }
}
