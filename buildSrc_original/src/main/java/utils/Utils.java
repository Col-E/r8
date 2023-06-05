// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package utils;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Utils {
  public static String toolsDir() {
    String osName = System.getProperty("os.name");
    if (osName.equals("Mac OS X")) {
      return "mac";
    } else if (osName.contains("Windows")) {
      return "windows";
    } else {
      return "linux";
    }
  }

  public static boolean isWindows() {
    return toolsDir().equals("windows");
  }

  public static Path dxExecutable() {
    String dxExecutableName = isWindows() ? "dx.bat" : "dx";
    return Paths.get("tools", toolsDir(), "dx", "bin", dxExecutableName);
  }

  public static Path dexMergerExecutable() {
    String executableName = isWindows() ? "dexmerger.bat" : "dexmerger";
    return Paths.get("tools", toolsDir(), "dx", "bin", executableName);
  }
}
