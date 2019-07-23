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

  protected boolean requiresCoreLibDesugaring(TestParameters parameters) {
    return parameters.getApiLevel().getLevel() < AndroidApiLevel.N.getLevel();
  }

  protected Path buildDesugaredLibrary(AndroidApiLevel apiLevel, List<Path> additionalProgramFiles)
      throws Exception {
    Path output = temp.newFolder().toPath().resolve("desugar_jdk_libs.zip");
    L8.run(
        L8Command.builder()
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
            .addProgramFiles(ToolHelper.getDesugarJDKLibs())
            .addProgramFiles(additionalProgramFiles)
            .addSpecialLibraryConfiguration("default")
            .setMinApiLevel(apiLevel.getLevel())
            .setOutput(output, OutputMode.DexIndexed)
            .build());
    return output;
  }

  protected Path buildDesugaredLibrary(AndroidApiLevel apiLevel) throws Exception {
    return buildDesugaredLibrary(apiLevel, ImmutableList.of());
  }

  protected void assertLines2By2Correct(String stdOut) {
    String[] lines = stdOut.split("\n");
    assert lines.length % 2 == 0;
    for (int i = 0; i < lines.length; i += 2) {
      assertEquals(lines[i], lines[i + 1]);
    }
  }
}
