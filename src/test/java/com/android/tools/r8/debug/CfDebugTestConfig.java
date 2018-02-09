// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Base test configuration with CF version of JDWP. */
public class CfDebugTestConfig extends DebugTestConfig {

  public static final Path JDWP_JAR = ToolHelper.getJdwpTestsCfJarPath(AndroidApiLevel.N);

  public CfDebugTestConfig() {
    this(Collections.emptyList());
  }

  public CfDebugTestConfig(Path... paths) {
    this(Arrays.asList(paths));
  }

  public CfDebugTestConfig(List<Path> paths) {
    addPaths(JDWP_JAR);
    addPaths(paths);
  }

  @Override
  public final RuntimeKind getRuntimeKind() {
    return RuntimeKind.CF;
  }
}
