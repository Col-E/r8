// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.VmTestRunner;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import regress_76025099.Main;

@RunWith(VmTestRunner.class)
public class B76025099 extends TestBase {

  private static final String PRG =
      ToolHelper.EXAMPLES_BUILD_DIR + "regress_76025099" + FileUtils.JAR_EXTENSION;

  private AndroidApp runR8(AndroidApp app, Class main, Path out) throws Exception {
     R8Command command =
        ToolHelper.addProguardConfigurationConsumer(
            ToolHelper.prepareR8CommandBuilder(app),
            pgConfig -> {
              pgConfig.setPrintMapping(true);
              pgConfig.setPrintMappingFile(out.resolve(ToolHelper.DEFAULT_PROGUARD_MAP_FILE));
            })
        .addProguardConfiguration(
            ImmutableList.of(keepMainProguardConfiguration(main)),
            Origin.unknown())
        .setOutput(out, OutputMode.DexIndexed)
        .build();
    return ToolHelper.runR8(command, o -> {
      o.enableMinification = false;
    });
  }

  @Ignore("b/76025099")
  @Test
  public void test() throws Exception {
    Path out = temp.getRoot().toPath();
    Path jarPath = Paths.get(PRG);
    String mainName = Main.class.getCanonicalName();
    ProcessResult jvmOutput = ToolHelper.runJava(ImmutableList.of(jarPath), mainName);
    assertEquals(0, jvmOutput.exitCode);
    AndroidApp processedApp = runR8(readJar(jarPath), Main.class, out);
    ProcessResult artOutput = runOnArtRaw(processedApp, mainName);
    assertEquals(0, artOutput.exitCode);
    assertEquals(jvmOutput.stdout, artOutput.stdout);
  }

}
