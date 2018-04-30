// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress.b78493232;

import com.android.tools.r8.AsmTestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm;
import org.junit.Assume;
import org.junit.Test;

public class Regress78493232 extends AsmTestBase {

  @Test
  public void test() throws Exception {
    // Run test on JVM and ART(x86) to ensure expected behavior.
    // Running the same test on an ARM JIT causes errors.
    Assume.assumeTrue(ToolHelper.getDexVm() != DexVm.ART_7_0_0_HOST); // b/78866151
    ensureSameOutput(
        Regress78493232Dump.CLASS_NAME,
        Regress78493232Dump.dump(),
        ToolHelper.getClassAsBytes(Regress78493232Utils.class));
  }
}
