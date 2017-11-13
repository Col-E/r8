// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.utils.VersionProperties;

public final class Version {

  public static final String LABEL = "v0.2.0-dev";

  private Version() {
  }

  public static void printToolVersion(String toolName) {
    System.out.println(toolName + " " + Version.LABEL);
    System.out.println(VersionProperties.INSTANCE.getDescription());
  }

  public static boolean isDev() {
    return LABEL.endsWith("-dev") || VersionProperties.INSTANCE.isEngineering();
  }
}
