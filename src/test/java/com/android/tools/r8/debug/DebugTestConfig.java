// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import static com.android.tools.r8.naming.ClassNameMapper.MissingFileAction.MISSING_FILE_IS_ERROR;

import com.android.tools.r8.naming.ClassNameMapper;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public abstract class DebugTestConfig {

  /** Valid runtime kinds for debuggee. */
  public enum RuntimeKind {
    CF,
    DEX
  }

  private boolean mustProcessAllCommands = true;
  private List<Path> paths = new ArrayList<>();

  private Path proguardMap = null;
  private ClassNameMapper.MissingFileAction missingProguardMapAction;

  /** The expected runtime kind for the debuggee. */
  public abstract RuntimeKind getRuntimeKind();

  public boolean isCfRuntime() {
    return getRuntimeKind() == RuntimeKind.CF;
  }

  public boolean isDexRuntime() {
    return getRuntimeKind() == RuntimeKind.DEX;
  }

  /** Classpath paths for the debuggee. */
  public List<Path> getPaths() {
    return paths;
  }

  public DebugTestConfig addPaths(Path... paths) {
    addPaths(Arrays.asList(paths));
    return this;
  }

  public DebugTestConfig addPaths(List<Path> paths) {
    this.paths.addAll(paths);
    return this;
  }

  public boolean mustProcessAllCommands() {
    return mustProcessAllCommands;
  }

  public void allowUnprocessedCommands() {
    mustProcessAllCommands = false;
  }

  /** Proguard map that the debuggee has been translated according to, null if not present. */
  public Path getProguardMap() {
    return proguardMap;
  }

  public ClassNameMapper.MissingFileAction getMissingProguardMapAction() {
    return missingProguardMapAction;
  }

  public DebugTestConfig setProguardMap(Path proguardMap) {
    return setProguardMap(proguardMap, MISSING_FILE_IS_ERROR);
  }

  public DebugTestConfig setProguardMap(
      Path proguardMap, ClassNameMapper.MissingFileAction missingProguardMapAction) {
    this.proguardMap = proguardMap;
    this.missingProguardMapAction = missingProguardMapAction;
    return this;
  }

  @Override
  public String toString() {
    StringBuilder builder =
        new StringBuilder()
            .append("DebugTestConfig{")
            .append("runtime:")
            .append(getRuntimeKind())
            .append(", classpath:[")
            .append(
                String.join(", ", paths.stream().map(Path::toString).collect(Collectors.toList())))
            .append("]");
    if (proguardMap != null) {
      builder.append(", pgmap:").append(proguardMap);
    }
    builder.append("}");
    return builder.toString();
  }
}
