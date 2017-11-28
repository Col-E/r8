// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.staticinterfacemethod;

import com.android.tools.r8.AsmTestBase;
import org.junit.Ignore;
import org.junit.Test;

public class MissingMethodTest extends AsmTestBase {

  @Ignore("b/69835274")
  @Test
  public void testDesugarInvokeMissingMethod() throws Exception {
    ensureException("Main", NoSuchMethodError.class, InterfaceDump.dump(), MainDump.dump());
  }
}
