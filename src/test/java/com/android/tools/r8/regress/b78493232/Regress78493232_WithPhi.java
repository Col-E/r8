// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress.b78493232;

import com.android.tools.r8.AsmTestBase;
import com.android.tools.r8.ToolHelper;
import org.junit.Test;

// Variant of Regress78493232, but where the new-instance is forced to flow to a non-trivial phi
// function prior to the call to <init>.
public class Regress78493232_WithPhi extends AsmTestBase {

  @Test
  public void test() throws Exception {
    // Run test on JVM and ART(x86) to ensure expected behavior.
    // Running the same test on an ARM JIT causes errors.
    ensureSameOutput(
        Regress78493232Dump_WithPhi.CLASS_NAME,
        Regress78493232Dump_WithPhi.dump(),
        ToolHelper.getClassAsBytes(Regress78493232Utils.class));
  }
}
