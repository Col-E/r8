// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.debug.CfDebugTestConfig;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.testing.AndroidBuildVersion;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.FileUtils;
import com.google.common.collect.ObjectArrays;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class JvmTestBuilder extends TestBuilder<JvmTestRunResult, JvmTestBuilder> {

  // Ordered list of classpath entries.
  private List<Path> classpath = new ArrayList<>();
  private List<String> vmArguments = new ArrayList<>();

  private AndroidApp.Builder builder = AndroidApp.builder();

  private JvmTestBuilder(TestState state) {
    super(state);
  }

  private Path writeClassesToJar(Collection<Class<?>> classes) {
    try {
      Path archive = getState().getNewTempFolder().resolve("out.jar");
      TestBase.writeClassesToJar(archive, classes);
      return archive;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static JvmTestBuilder create(TestState state) {
    return new JvmTestBuilder(state);
  }

  @Override
  JvmTestBuilder self() {
    return this;
  }

  @Override
  @Deprecated
  public JvmTestRunResult run(String mainClass) throws IOException {
    return run(TestRuntime.getDefaultJavaRuntime(), mainClass);
  }

  @Override
  public JvmTestRunResult run(TestRuntime runtime, String mainClass, String... args)
      throws IOException {
    assert runtime.isCf();
    ProcessResult result =
        ToolHelper.runJava(
            runtime.asCf(), vmArguments, classpath, ObjectArrays.concat(mainClass, args));
    return new JvmTestRunResult(builder.build(), runtime, result, getState());
  }

  @Override
  public CfDebugTestConfig debugConfig(TestRuntime runtime) throws Exception {
    assert runtime.isCf();
    return new CfDebugTestConfig(runtime.asCf(), classpath);
  }

  @Override
  public JvmTestBuilder addLibraryFiles(Collection<Path> files) {
    return addRunClasspathFiles(files);
  }

  @Override
  public JvmTestBuilder addLibraryClasses(Collection<Class<?>> classes) {
    return addRunClasspathFiles(writeClassesToJar(classes));
  }

  @Override
  public JvmTestBuilder addClasspathClasses(Collection<Class<?>> classes) {
    return addClasspath(writeClassesToJar(classes));
  }

  @Override
  public JvmTestBuilder addClasspathFiles(Collection<Path> files) {
    return addClasspath(files);
  }

  @Override
  public JvmTestBuilder addRunClasspathFiles(Collection<Path> files) {
    return addClasspath(files);
  }

  @Override
  public JvmTestBuilder addProgramClasses(Collection<Class<?>> classes) {
    return addProgramFiles(writeClassesToJar(classes));
  }

  @Override
  public JvmTestBuilder addProgramFiles(Collection<Path> files) {
    for (Path file : files) {
      if (FileUtils.isArchive(file)) {
        classpath.add(file);
        builder.addProgramFiles(file);
      } else if (FileUtils.isClassFile(file)) {
        try {
          addProgramClassFileData(Files.readAllBytes(file));
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      } else {
        assert Files.isDirectory(file);
        classpath.add(file);
      }
    }
    return self();
  }

  @Override
  public JvmTestBuilder addProgramClassFileData(Collection<byte[]> files) {
    try {
      Path out = getState().getNewTempFolder().resolve("out.jar");
      TestBase.writeClassFileDataToJar(out, files);
      addProgramFiles(out);
      return self();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public JvmTestBuilder addProgramDexFileData(Collection<byte[]> data) {
    throw new Unimplemented("No support for adding dex file data directly");
  }

  public JvmTestBuilder addClasspath(Path... paths) {
    return addClasspath(Arrays.asList(paths));
  }

  public JvmTestBuilder addClasspath(Collection<Path> paths) {
    for (Path path : paths) {
      assert Files.isDirectory(path) || FileUtils.isArchive(path);
      classpath.add(path);
    }
    return self();
  }

  public JvmTestBuilder addTestClasspath() {
    return addClasspath(ToolHelper.getClassPathForTests());
  }

  public JvmTestBuilder enablePreview() {
    addVmArguments("--enable-preview");
    return self();
  }

  public JvmTestBuilder configureJaCoCoAgentForOfflineInstrumentedCode(
      Path jacocoAgent, Path output) {
    addProgramFiles(jacocoAgent);
    addVmArguments(
        "-Djacoco-agent.destfile=" + output.toString(),
        "-Djacoco-agent.dumponexit=true",
        "-Djacoco-agent.output=file");
    return self();
  }

  public JvmTestBuilder enableJaCoCoAgent(Path jacocoAgent, Path output) {
    addProgramFiles(jacocoAgent);
    addVmArguments(
        String.format(
            "-javaagent:%s=destfile=%s,dumponexit=true,output=file", jacocoAgent, output));
    return self();
  }

  public JvmTestBuilder addVmArguments(Collection<String> arguments) {
    vmArguments.addAll(arguments);
    return self();
  }

  public JvmTestBuilder addVmArguments(String... arguments) {
    return addVmArguments(Arrays.asList(arguments));
  }

  public JvmTestBuilder disassemble() throws Exception {
    ToolHelper.disassemble(builder.build(), System.out);
    return self();
  }

  public JvmTestBuilder noVerify() {
    return addVmArguments("-noverify");
  }

  public JvmTestBuilder addAndroidBuildVersion() {
    return addAndroidBuildVersion(AndroidApiLevel.ANDROID_PLATFORM);
  }

  public JvmTestBuilder addAndroidBuildVersion(AndroidApiLevel apiLevel) {
    addVmArguments("-D" + AndroidBuildVersion.PROPERTY + "=" + apiLevel.getLevel());
    return addProgramClasses(AndroidBuildVersion.class);
  }
}
