// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress.b77842465;

import com.android.tools.r8.AsmTestBase;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.origin.Origin;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.Test;

public class Regress77842465 extends AsmTestBase {

  @Test
  public void test() throws CompilationFailedException, IOException {

    Path dexOut = temp.getRoot().toPath().resolve("out.jar");
    Path oatOut = temp.getRoot().toPath().resolve("out.odex");

    D8.run(D8Command.builder()
        .addClassProgramData(Regress77842465Dump.dump(), Origin.unknown())
        .setOutput(dexOut, OutputMode.DexIndexed)
        .setDisableDesugaring(true)
        .build());

    ToolHelper.runDex2Oat(dexOut, oatOut);
  }
}
