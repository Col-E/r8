// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.AsmTestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.invokespecial.Main;
import com.android.tools.r8.graph.invokespecial.TestClassDump;
import org.junit.Ignore;
import org.junit.Test;

public class InvokeSpecialTest extends AsmTestBase {

  @Ignore("b/110175213")
  @Test
  public void testInvokeSpecial() throws Exception {
    ensureSameOutput(
        Main.class.getCanonicalName(),
        ToolHelper.getClassAsBytes(Main.class),
        TestClassDump.dump());
  }
}
