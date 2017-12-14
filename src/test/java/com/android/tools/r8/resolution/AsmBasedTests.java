// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution;

import com.android.tools.r8.AsmTestBase;
import com.android.tools.r8.resolution.shadowing1.AClassDump;
import com.android.tools.r8.resolution.shadowing1.InterfaceDump;
import com.android.tools.r8.resolution.shadowing1.MainDump;
import com.android.tools.r8.resolution.shadowing1.SubInterfaceDump;
import org.junit.Ignore;
import org.junit.Test;

public class AsmBasedTests extends AsmTestBase {

  @Test
  @Ignore("b/69356146")
  public void defaultMethodShadowedByStatic() throws Exception {
    ensureException("Main", IncompatibleClassChangeError.class,
        InterfaceDump.dump(),
        SubInterfaceDump.dump(),
        AClassDump.dump(),
        MainDump.dump());
  }

  @Test
  @Ignore("b/69356146")
  public void invokeDefaultMethodViaStatic() throws Exception {
    ensureException("Main", IncompatibleClassChangeError.class,
        com.android.tools.r8.resolution.invokestaticinterfacedefault.InterfaceDump.dump(),
        com.android.tools.r8.resolution.invokestaticinterfacedefault.MainDump.dump());

  }
}
