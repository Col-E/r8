// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.ToolHelper.isWindows;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper.ProcessResult;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.rules.TemporaryFolder;

public class JavaCompilerTool {

  private final CfVm jdk;
  private final TestState state;
  private final List<Path> sources = new ArrayList<>();
  private final List<Path> classpath = new ArrayList<>();
  private final List<String> options = new ArrayList<>();
  private Path output = null;

  private JavaCompilerTool(CfVm jdk, TestState state) {
    this.jdk = jdk;
    this.state = state;
  }

  public static JavaCompilerTool create(CfVm jdk, TemporaryFolder temp) {
    assert temp != null;
    return create(jdk, new TestState(temp));
  }

  public static JavaCompilerTool create(CfVm jdk, TestState state) {
    assert state != null;
    return new JavaCompilerTool(jdk, state);
  }

  public JavaCompilerTool addOptions(String... options) {
    return addOptions(Arrays.asList(options));
  }

  public JavaCompilerTool addOptions(Collection<String> options) {
    this.options.addAll(options);
    return this;
  }

  public JavaCompilerTool addSourceFiles(Path... files) {
    return addSourceFiles(Arrays.asList(files));
  }

  public JavaCompilerTool addSourceFiles(Collection<Path> files) {
    sources.addAll(files);
    return this;
  }

  public JavaCompilerTool addClasspathFiles(Path... files) {
    return addClasspathFiles(Arrays.asList(files));
  }

  public JavaCompilerTool addClasspathFiles(Collection<Path> files) {
    classpath.addAll(files);
    return this;
  }

  public JavaCompilerTool setOutputPath(Path file) {
    this.output = file;
    return this;
  }

  private Path getOrCreateOutputPath() throws IOException {
    return output != null ? output : state.getNewTempFolder().resolve("out.jar");
  }

  /** Compile and return the compilations process result object. */
  public ProcessResult compile() throws IOException {
    return compileInternal(getOrCreateOutputPath());
  }

  /** Compile asserting success and return the output path. */
  public Path compileAndWrite() throws IOException {
    Path output = getOrCreateOutputPath();
    compileAndWriteTo(output);
    return output;
  }

  /** Compile asserting success and write to the given output path. */
  public void compileAndWriteTo(Path output) throws IOException {
    ProcessResult result = compileInternal(output);
    assertEquals(result.toString(), result.exitCode, 0);
  }

  private ProcessResult compileInternal(Path output) throws IOException {
    List<String> cmdline = new ArrayList<>();
    cmdline.add(ToolHelper.getJavaExecutable(jdk) + "c");
    cmdline.addAll(options);
    if (!classpath.isEmpty()) {
      cmdline.add("-cp");
      cmdline.add(
          classpath.stream()
              .map(Path::toString)
              .collect(Collectors.joining(isWindows() ? ";" : ":")));
    }
    cmdline.add("-d");
    cmdline.add(output.toString());
    for (Path source : sources) {
      cmdline.add(source.toString());
    }
    ProcessBuilder builder = new ProcessBuilder(cmdline);
    return ToolHelper.runProcess(builder);
  }
}
