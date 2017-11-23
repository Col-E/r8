// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.OutputMode;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.rules.TemporaryFolder;

public class D8BaseDebugTestConfig extends DebugTestConfig {

  public static final Path JDWP_JAR =
      ToolHelper.getJdwpTestsJarPath(ToolHelper.getMinApiLevelForDexVm(ToolHelper.getDexVm()));

  // Internal cache to avoid multiple compilations of the base JDWP code.
  private static AndroidApp compiledJdwp = null;

  private static synchronized AndroidApp getCompiledJdwp() {
    if (compiledJdwp == null) {
      int minSdk = ToolHelper.getMinApiLevelForDexVm(ToolHelper.getDexVm());
      try {
        compiledJdwp =
            ToolHelper.runD8(
                D8Command.builder()
                    .addProgramFiles(JDWP_JAR)
                    .setMinApiLevel(minSdk)
                    .setMode(CompilationMode.DEBUG)
                    .addLibraryFiles(Paths.get(ToolHelper.getAndroidJar(minSdk)))
                    .build());
      } catch (Throwable e) {
        throw new RuntimeException(e);
      }
    }
    return compiledJdwp;
  }

  private final List<Path> paths = new ArrayList<>();

  private Path proguardMap = null;

  public D8BaseDebugTestConfig(TemporaryFolder temp) {
    this(temp, ImmutableList.of());
  }

  public D8BaseDebugTestConfig(TemporaryFolder temp, Path... paths) {
    this(temp, Arrays.asList(paths));
  }

  public D8BaseDebugTestConfig(TemporaryFolder temp, List<Path> paths) {
    addPaths(paths);
    try {
      Path out = temp.newFolder().toPath().resolve("d8_jdwp.jar");
      getCompiledJdwp().write(out, OutputMode.Indexed);
      addPaths(out);
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public RuntimeKind getRuntimeKind() {
    return RuntimeKind.DEX;
  }

  @Override
  public List<Path> getPaths() {
    return paths;
  }

  @Override
  public Path getProguardMap() {
    return proguardMap;
  }

  public void addPaths(Path... paths) {
    addPaths(Arrays.asList(paths));
  }

  public void addPaths(List<Path> paths) {
    this.paths.addAll(paths);
  }

  public void setProguardMap(Path proguardMap) {
    this.proguardMap = proguardMap;
  }
}
