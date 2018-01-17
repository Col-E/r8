// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.rewrite.switchmaps;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DexInspector;
import com.google.common.collect.ImmutableList;
import java.nio.file.Paths;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class RewriteSwitchMapsTest extends TestBase {

  private static final String JAR_FILE = "switchmaps.jar";
  private static final String SWITCHMAP_CLASS_NAME = "switchmaps.Switches$1";
  private static final List<String> PG_CONFIG = ImmutableList.of(
      "-keep class switchmaps.Switches { public static void main(...); }",
      "-dontobfuscate",
      "-keepattributes *");

  @Test
  public void checkSwitchMapsRemoved() throws Exception {
    run(CompilationMode.RELEASE);
  }

  @Test
  public void checkSwitchMapsRemovedDebug() throws Exception {
    run(CompilationMode.DEBUG);
  }

  private void run(CompilationMode compilationMode) throws Exception {
    R8Command command = R8Command.builder()
        .addProgramFiles(Paths.get(ToolHelper.EXAMPLES_BUILD_DIR).resolve(JAR_FILE))
        .addLibraryFiles(ToolHelper.getDefaultAndroidJar())
        .addProguardConfiguration(PG_CONFIG, Origin.unknown())
        .setMode(compilationMode)
        .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
        .build();
    AndroidApp result = ToolHelper.runR8(command);
    DexInspector inspector = new DexInspector(result);
    Assert.assertFalse(inspector.clazz(SWITCHMAP_CLASS_NAME).isPresent());
  }
}
