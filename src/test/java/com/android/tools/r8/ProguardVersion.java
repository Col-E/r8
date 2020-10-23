// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static com.android.tools.r8.ToolHelper.isWindows;

import java.nio.file.Path;
import java.nio.file.Paths;

public enum ProguardVersion {
  V5_2_1("5.2.1"),
  V6_0_1("6.0.1"),
  V7_0_0("7.0.0");

  private final String version;

  ProguardVersion(String version) {
    this.version = version;
  }

  public static ProguardVersion getLatest() {
    return V7_0_0;
  }

  public Path getProguardScript() {
    Path scriptDirectory = Paths.get(ToolHelper.THIRD_PARTY_DIR).resolve("proguard");
    if (this == V7_0_0) {
      scriptDirectory = scriptDirectory.resolve("proguard-" + version);
    } else {
      scriptDirectory = scriptDirectory.resolve("proguard" + version);
    }
    if (isWindows()) {
      return scriptDirectory.resolve("bin/proguard.bat");
    }
    return scriptDirectory.resolve("bin/proguard.sh");
  }

  public String getVersion() {
    return version;
  }

  @Override
  public String toString() {
    return "Proguard " + version;
  }
}
