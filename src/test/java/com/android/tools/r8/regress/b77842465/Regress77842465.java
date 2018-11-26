// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress.b77842465;

import com.android.tools.r8.AsmTestBase;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.io.IOException;
import org.junit.Test;

public class Regress77842465 extends AsmTestBase {

  @Test
  public void test() throws CompilationFailedException, IOException {
    testForD8()
        .addProgramClassFileData(Regress77842465Dump.dump())
        .noDesugaring()
        .setMinApi(AndroidApiLevel.M)
        .compile()
        .runDex2Oat()
        .assertSuccess();
  }
}
