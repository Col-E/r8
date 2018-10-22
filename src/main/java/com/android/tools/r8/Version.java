// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.utils.VersionProperties;

/** Version of the D8/R8 library. */
public final class Version {

  // This field is accessed from release scripts using simple pattern matching.
  // Therefore, changing this field could break our release scripts.
  public static final String LABEL = "1.4.3-dev";

  private Version() {
  }

  public static void printToolVersion(String toolName) {
    System.out.println(toolName + " " + Version.LABEL);
    System.out.println(VersionProperties.INSTANCE.getDescription());
  }

  /** Is this a development version of the D8/R8 library. */
  public static boolean isDev() {
    return LABEL.endsWith("-dev") || VersionProperties.INSTANCE.isEngineering();
  }
}
