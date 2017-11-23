// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.nio.file.Path;

public class CfDebugTestConfig extends DebugTestConfig {

  public static final Path JDWP_JAR = ToolHelper.getJdwpTestsJarPath(AndroidApiLevel.N.getLevel());

  public CfDebugTestConfig() {
    addPaths(JDWP_JAR);
  }

  @Override
  public RuntimeKind getRuntimeKind() {
    return RuntimeKind.CF;
  }
}
