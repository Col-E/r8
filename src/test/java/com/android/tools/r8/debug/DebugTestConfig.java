// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import java.nio.file.Path;
import java.util.List;

public abstract class DebugTestConfig {

  public enum RuntimeKind {
    CF,
    DEX
  }

  public abstract RuntimeKind getRuntimeKind();

  public abstract List<Path> getPaths();

  public abstract Path getProguardMap();

  public boolean isCfRuntime() {
    return getRuntimeKind() == RuntimeKind.CF;
  }

  public boolean isDexRuntime() {
    return getRuntimeKind() == RuntimeKind.DEX;
  }
}
