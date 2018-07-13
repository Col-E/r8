// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.compatproguard;

import com.android.tools.r8.CompatProguardCommandBuilder;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.smali.SmaliBuilder;
import com.android.tools.r8.smali.SmaliTestBase;
import com.android.tools.r8.utils.dexinspector.DexInspector;
import java.nio.file.Path;
import java.util.List;

class CompatProguardSmaliTestBase extends SmaliTestBase {
  DexInspector runCompatProguard(SmaliBuilder builder, List<String> proguardConfigurations)
      throws Exception {
    Path dexOutputDir = temp.newFolder().toPath();
    R8Command.Builder commandBuilder =
        new CompatProguardCommandBuilder(true)
            .setOutput(dexOutputDir, OutputMode.DexIndexed)
            .addProguardConfiguration(proguardConfigurations, Origin.unknown());
    ToolHelper.getAppBuilder(commandBuilder).addDexProgramData(builder.compile(), Origin.unknown());
    return new DexInspector(ToolHelper.runR8(commandBuilder.build()));
  }
}
