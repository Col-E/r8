// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.ToolHelper;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Base test configuration with DEX version of JDWP. */
public class DexDebugTestConfig extends DebugTestConfig {

  public static final Path JDWP_DEX_JAR =
      ToolHelper.getJdwpTestsDexJarPath(ToolHelper.getMinApiLevelForDexVm(ToolHelper.getDexVm()));

  public DexDebugTestConfig() {
    this(Collections.emptyList());
  }

  public DexDebugTestConfig(Path... paths) {
    this(Arrays.asList(paths));
  }

  public DexDebugTestConfig(List<Path> paths) {
    addPaths(JDWP_DEX_JAR);
    addPaths(paths);
  }

  @Override
  public final RuntimeKind getRuntimeKind() {
    return RuntimeKind.DEX;
  }
}
