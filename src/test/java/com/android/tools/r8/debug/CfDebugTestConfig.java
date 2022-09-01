// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Base test configuration with CF version of JDWP. */
public class CfDebugTestConfig extends DebugTestConfig {

  public static final Path JDWP_JAR = ToolHelper.getJdwpTestsCfJarPath(AndroidApiLevel.N);

  private final CfRuntime runtime;

  public CfDebugTestConfig() {
    this(Collections.emptyList());
  }

  public CfDebugTestConfig(Path... paths) {
    this(Arrays.asList(paths));
  }

  @Deprecated
  public CfDebugTestConfig(List<Path> paths) {
    this(TestRuntime.getDefaultCfRuntime(), paths);
  }

  public CfDebugTestConfig(CfRuntime runtime, List<Path> paths) {
    this.runtime = runtime;
    addPaths(JDWP_JAR);
    addPaths(paths);
  }

  @Override
  public final CfRuntime getRuntime() {
    return runtime;
  }
}
