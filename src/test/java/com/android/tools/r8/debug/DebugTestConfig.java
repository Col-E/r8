// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class DebugTestConfig {

  /** Valid runtime kinds for debuggee. */
  public enum RuntimeKind {
    CF,
    DEX
  }

  private List<Path> paths = new ArrayList<>();

  private Path proguardMap = null;

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

  public void addPaths(Path... paths) {
    addPaths(Arrays.asList(paths));
  }

  public void addPaths(List<Path> paths) {
    this.paths.addAll(paths);
  }

  /** Proguard map that the debuggee has been translated according to, null if not present. */
  public Path getProguardMap() {
    return proguardMap;
  }

  public void setProguardMap(Path proguardMap) {
    this.proguardMap = proguardMap;
  }
}
