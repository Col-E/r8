// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.OutputMode;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import org.junit.rules.TemporaryFolder;

/**
 * Base test configuration for running a DEX debuggee with a D8 compiled version of JDWP.
 *
 * <p>This class also provides utilities for compiling with D8 and adding it to the classpath.
 */
public class D8DebugTestConfig extends DebugTestConfig {

  public static final Path JDWP_JAR =
      ToolHelper.getJdwpTestsJarPath(ToolHelper.getMinApiLevelForDexVm(ToolHelper.getDexVm()));

  // Internal cache to avoid multiple compilations of the base JDWP code.
  private static AndroidApp compiledJdwp = null;

  private static synchronized AndroidApp getCompiledJdwp() {
    if (compiledJdwp == null) {
      compiledJdwp = compile(Collections.singletonList(JDWP_JAR), null);
    }
    return compiledJdwp;
  }

  public static AndroidApp compile(List<Path> paths, Consumer<InternalOptions> optionsConsumer) {
    try {
      int minSdk = ToolHelper.getMinApiLevelForDexVm(ToolHelper.getDexVm());
      return ToolHelper.runD8(
          D8Command.builder()
              .addProgramFiles(paths)
              .setMinApiLevel(minSdk)
              .setMode(CompilationMode.DEBUG)
              .addLibraryFiles(Paths.get(ToolHelper.getAndroidJar(minSdk)))
              .build(),
          optionsConsumer);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static D8DebugTestConfig fromUncompiledPaths(TemporaryFolder temp, List<Path> paths) {
    D8DebugTestConfig config = new D8DebugTestConfig(temp);
    config.compileAndAddPaths(temp, paths);
    return config;
  }

  public static D8DebugTestConfig fromCompiledPaths(TemporaryFolder temp, List<Path> paths) {
    D8DebugTestConfig config = new D8DebugTestConfig(temp);
    config.addPaths(paths);
    return config;
  }

  public D8DebugTestConfig(TemporaryFolder temp) {
    try {
      Path out = temp.newFolder().toPath().resolve("d8_jdwp.jar");
      getCompiledJdwp().write(out, OutputMode.Indexed);
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public RuntimeKind getRuntimeKind() {
    return RuntimeKind.DEX;
  }

  public void compileAndAddPaths(TemporaryFolder temp, List<Path> paths) {
    compileAndAddPaths(temp, paths, null);
  }

  public void compileAndAddPaths(
      TemporaryFolder temp, List<Path> paths, Consumer<InternalOptions> optionsConsumer) {
    try {
      Path out = temp.newFolder().toPath().resolve("d8_compiled.jar");
      compile(paths, optionsConsumer).write(out, OutputMode.Indexed);
      addPaths(out);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
