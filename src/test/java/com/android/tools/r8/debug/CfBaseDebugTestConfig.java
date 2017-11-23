// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;

public class CfBaseDebugTestConfig extends DebugTestConfig {

  public static final Path JDWP_JAR = ToolHelper.getJdwpTestsJarPath(AndroidApiLevel.N.getLevel());

  @Override
  public RuntimeKind getRuntimeKind() {
    return RuntimeKind.CF;
  }

  @Override
  public List<Path> getPaths() {
    return ImmutableList.of(JDWP_JAR);
  }

  @Override
  public Path getProguardMap() {
    return null;
  }
}
