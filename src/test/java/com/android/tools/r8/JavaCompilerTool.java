// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.ToolHelper.isWindows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.ZipUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.rules.TemporaryFolder;

public class JavaCompilerTool {

  private final CfRuntime jdk;
  private final TestState state;
  private String source = null;
  private String target = null;
  private final List<Path> sources = new ArrayList<>();
  private final List<String> classNames = new ArrayList<>();
  private final List<Path> classpath = new ArrayList<>();
  private final List<String> options = new ArrayList<>();
  private final List<Path> annotationProcessorPath = new ArrayList<>();
  private final List<String> annotationProcessors = new ArrayList<>();
  private Path output = null;

  private JavaCompilerTool(CfRuntime jdk, TestState state) {
    this.jdk = jdk;
    this.state = state;
  }

  public static JavaCompilerTool create(CfRuntime jdk, TemporaryFolder temp) {
    assert temp != null;
    return create(jdk, new TestState(temp));
  }

  public static JavaCompilerTool create(CfRuntime jdk, TestState state) {
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

  public JavaCompilerTool setSource(String source) {
    this.source = source;
    return this;
  }

  public JavaCompilerTool setTarget(String target) {
    this.target = target;
    return this;
  }

  public JavaCompilerTool addSourceFiles(Path... files) {
    return addSourceFiles(Arrays.asList(files));
  }

  public JavaCompilerTool addSourceFiles(Collection<Path> files) {
    sources.addAll(files);
    return this;
  }

  public JavaCompilerTool addClassNames(Collection<String> classNames) {
    this.classNames.addAll(classNames);
    return this;
  }

  public JavaCompilerTool addClasspathFiles(Path... files) {
    return addClasspathFiles(Arrays.asList(files));
  }

  public JavaCompilerTool addClasspathFiles(Collection<Path> files) {
    classpath.addAll(files);
    return this;
  }

  public JavaCompilerTool addAnnotationProcessorPathFiles(Path... files) {
    return addAnnotationProcessorPathFiles(Arrays.asList(files));
  }

  public JavaCompilerTool addAnnotationProcessorPathFiles(Collection<Path> files) {
    annotationProcessorPath.addAll(files);
    return this;
  }

  public JavaCompilerTool addAnnotationProcessors(String... processors) {
    return addAnnotationProcessors(Arrays.asList(processors));
  }

  public JavaCompilerTool addAnnotationProcessors(Collection<String> processors) {
    annotationProcessors.addAll(processors);
    return this;
  }

  /** Set the output. Must be to an existing directory or to an non-existing jar file. */
  public JavaCompilerTool setOutputPath(Path file) {
    assert (Files.exists(file) && Files.isDirectory(file))
        || (!Files.exists(file) && FileUtils.isJarFile(file) && Files.exists(file.getParent()));
    this.output = file;
    return this;
  }

  public JavaCompilerTool addDebugAgent() {
    return addOptions(
        "-J-debug", "-J-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005");
  }

  private Path getOrCreateOutputPath() throws IOException {
    return output != null ? output : state.getNewTempFolder().resolve("out.jar");
  }

  /** Compile and return the compilations process result object. */
  public ProcessResult compileRaw() throws IOException {
    assertNotNull("An output path must be specified prior to compilation.", output);
    return compileInternal(output);
  }

  /**
   * Compile asserting success and return the path to the output file.
   *
   * <p>If no output file has been set, the output is compiled to a zip archive in a temporary
   * directory.
   */
  public Path compile() throws IOException {
    Path output = getOrCreateOutputPath();
    ProcessResult result = compileInternal(output);
    assertEquals(result.toString(), result.exitCode, 0);
    return output;
  }

  // For javac command line options see
  // https://docs.oracle.com/en/java/javase/17/docs/specs/man/javac.html.
  private ProcessResult compileInternal(Path output) throws IOException {
    Path outdir = Files.isDirectory(output) ? output : state.getNewTempFolder();
    List<String> cmdline = new ArrayList<>();
    cmdline.add(jdk.getJavaExecutable() + "c");
    cmdline.addAll(options);
    if (!classpath.isEmpty()) {
      cmdline.add("-cp");
      cmdline.add(
          classpath.stream()
              .map(Path::toString)
              .collect(Collectors.joining(isWindows() ? ";" : ":")));
    }
    if (!annotationProcessorPath.isEmpty()) {
      cmdline.add("-processorpath");
      cmdline.add(
          annotationProcessorPath.stream()
              .map(Path::toString)
              .collect(Collectors.joining(isWindows() ? ";" : ":")));
    }
    if (!annotationProcessors.isEmpty()) {
      cmdline.add("-processor");
      cmdline.add(String.join(",", annotationProcessors));
    }
    if (source != null) {
      cmdline.add("-source");
      cmdline.add(source);
    }
    if (target != null) {
      cmdline.add("-target");
      cmdline.add(target);
    }
    cmdline.add("-d");
    cmdline.add(outdir.toString());
    for (Path source : sources) {
      cmdline.add(source.toString());
    }
    cmdline.addAll(classNames);
    ProcessBuilder builder = new ProcessBuilder(cmdline);
    ProcessResult javacResult = ToolHelper.runProcess(builder);
    if (FileUtils.isJarFile(output)) {
      assert !outdir.equals(output);
      ZipUtils.zip(output, outdir);
    }
    return javacResult;
  }
}
