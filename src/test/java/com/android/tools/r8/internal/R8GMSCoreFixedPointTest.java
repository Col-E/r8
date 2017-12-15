// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import java.nio.file.Paths;
import org.junit.Test;

public class R8GMSCoreFixedPointTest extends GMSCoreCompilationTestBase {

  @Test
  public void fixedPoint() throws Exception {
    // First compilation.
    AndroidApp app =
        AndroidApp.builder()
            .addProgramDirectory(Paths.get(GMSCORE_V7_DIR))
            .setProguardMapFile(null)
            .build();

    AndroidApp app1 =
        ToolHelper.runR8(
            ToolHelper.prepareR8CommandBuilder(app)
                .setMode(CompilationMode.DEBUG)
                .setMinApiLevel(AndroidApiLevel.L.getLevel())
                .build(),
            options -> options.ignoreMissingClasses = true);

    // Second compilation.
    // Add option --skip-outline-opt for second compilation. The second compilation can find
    // additional outlining opportunities as member rebinding from the first compilation can move
    // methods.
    // See b/33410508 and b/33475705.
    AndroidApp app2 =
        ToolHelper.runR8(
            ToolHelper.prepareR8CommandBuilder(app1)
                .setMode(CompilationMode.DEBUG)
                .setMinApiLevel(AndroidApiLevel.L.getLevel())
                .build(),
            options -> options.ignoreMissingClasses = true);

    assertIdenticalApplicationsUpToCode(app1, app2, false);
  }
}
