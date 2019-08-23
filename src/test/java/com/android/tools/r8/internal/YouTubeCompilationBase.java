// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.R8RunArtTestsTest.CompilerUnderTest;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public abstract class YouTubeCompilationBase extends CompilationTestBase {

  static final String APK = "YouTubeRelease_unsigned.apk";
  static final String DEPLOY_JAR = "YouTubeRelease_deploy.jar";
  static final String PG_JAR = "YouTubeRelease_proguard.jar";
  static final String PG_MAP = "YouTubeRelease_proguard.map";
  static final String PG_CONF = "YouTubeRelease_proguard.config";

  final String base;

  public YouTubeCompilationBase(int majorVersion, int minorVersion) {
    this.base = "third_party/youtube/youtube.android_" + majorVersion + "." + minorVersion + "/";
  }

  public List<Path> getKeepRuleFiles() {
    return ImmutableList.of(
        Paths.get(base).resolve(PG_CONF),
        Paths.get(ToolHelper.PROGUARD_SETTINGS_FOR_INTERNAL_APPS).resolve(PG_CONF));
  }

  public List<Path> getProgramFiles() throws IOException {
    List<Path> result = new ArrayList<>();
    for (Path keepRuleFile : getKeepRuleFiles()) {
      for (String line : FileUtils.readAllLines(keepRuleFile)) {
        if (line.startsWith("-injars")) {
          String fileName = line.substring("-injars ".length());
          result.add(Paths.get(base).resolve(fileName));
        }
      }
    }
    return result;
  }

  void runR8AndCheckVerification(CompilationMode mode, String input) throws Exception {
    runAndCheckVerification(
        CompilerUnderTest.R8, mode, base + APK, null, null, ImmutableList.of(base + input));
  }
}
