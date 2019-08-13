// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.corelib;

import static junit.framework.TestCase.assertEquals;

import com.android.tools.r8.L8;
import com.android.tools.r8.L8Command;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;

public class CoreLibDesugarTestBase extends TestBase {

  @Deprecated
  protected boolean requiresCoreLibDesugaring(TestParameters parameters) {
    // TODO(b/134732760): Use the two other APIS instead.
    return requiresEmulatedInterfaceCoreLibDesugaring(parameters)
        && requiresRetargetCoreLibMemberDesugaring(parameters);
  }

  protected boolean requiresEmulatedInterfaceCoreLibDesugaring(TestParameters parameters) {
    return parameters.getApiLevel().getLevel() < AndroidApiLevel.N.getLevel();
  }

  protected boolean requiresRetargetCoreLibMemberDesugaring(TestParameters parameters) {
    return parameters.getApiLevel().getLevel() < AndroidApiLevel.P.getLevel();
  }

  protected boolean requiresAnyCoreLibDesugaring(TestParameters parameters) {
    return requiresRetargetCoreLibMemberDesugaring(parameters);
  }

  protected Path buildDesugaredLibrary(AndroidApiLevel apiLevel) throws RuntimeException {
    return buildDesugaredLibrary(apiLevel, "", false);
  }

  protected Path buildDesugaredLibrary(AndroidApiLevel apiLevel, String keepRules)
      throws RuntimeException {
    return buildDesugaredLibrary(apiLevel, keepRules, true);
  }

  protected Path buildDesugaredLibrary(AndroidApiLevel apiLevel, String keepRules, boolean shrink)
      throws RuntimeException {
    return buildDesugaredLibrary(apiLevel, keepRules, shrink, ImmutableList.of());
  }

  protected Path buildDesugaredLibrary(
      AndroidApiLevel apiLevel, String keepRules, boolean shrink, List<Path> additionalProgramFiles)
      throws RuntimeException {
    // TODO(b/134732760): Support Shrinking.
    // We wrap exceptions in a RuntimeException to call this from a lambda.
    try {
      Path desugaredLib = temp.newFolder().toPath().resolve("desugar_jdk_libs_dex.zip");
      L8.run(
          L8Command.builder()
              .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
              .addProgramFiles(ToolHelper.getDesugarJDKLibs())
              .addProgramFiles(additionalProgramFiles)
              .addSpecialLibraryConfiguration("default")
              .setMinApiLevel(apiLevel.getLevel())
              .setOutput(desugaredLib, OutputMode.DexIndexed)
              .build());
      return desugaredLib;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  protected void assertLines2By2Correct(String stdOut) {
    String[] lines = stdOut.split("\n");
    assert lines.length % 2 == 0;
    for (int i = 0; i < lines.length; i += 2) {
      assertEquals(lines[i], lines[i + 1]);
    }
  }
}
