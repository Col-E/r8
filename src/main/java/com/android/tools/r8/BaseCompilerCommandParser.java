// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.StringDiagnostic;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class BaseCompilerCommandParser {

  static void parseMinApi(BaseCompilerCommand.Builder builder, String minApiString, Origin origin) {
    int minApi;
    try {
      minApi = Integer.parseInt(minApiString);
    } catch (NumberFormatException e) {
      builder.error(new StringDiagnostic("Invalid argument to --min-api: " + minApiString, origin));
      return;
    }
    if (minApi < 1) {
      builder.error(new StringDiagnostic("Invalid argument to --min-api: " + minApiString, origin));
      return;
    }
    builder.setMinApiLevel(minApi);
  }

  /**
   * This method must match the lookup in
   * {@link com.android.tools.r8.JdkClassFileProvider#fromJdkHome}.
   */
  private static boolean isJdkHome(Path home) {
    Path jrtFsJar = home.resolve("lib").resolve("jrt-fs.jar");
    if (Files.exists(jrtFsJar)) {
      return true;
    }
    // JDK has rt.jar in jre/lib/rt.jar.
    Path rtJar = home.resolve("jre").resolve("lib").resolve("rt.jar");
    if (Files.exists(rtJar)) {
      return true;
    }
    // JRE has rt.jar in lib/rt.jar.
    rtJar = home.resolve("lib").resolve("rt.jar");
    if (Files.exists(rtJar)) {
      return true;
    }
    return false;
  }

  static void addLibraryArgument(BaseCommand.Builder builder, Origin origin, String arg) {
    Path path = Paths.get(arg);
    if (isJdkHome(path)) {
      try {
        builder
            .addLibraryResourceProvider(JdkClassFileProvider.fromJdkHome(path));
      } catch (IOException e) {
        builder.error(new ExceptionDiagnostic(e, origin));
      }
    } else {
      builder.addLibraryFiles(path);
    }
  }
}
