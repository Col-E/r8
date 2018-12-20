// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.VmTestRunner;
import com.android.tools.r8.VmTestRunner.IgnoreIfVmOlderThan;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VmTestRunner.class)
public class ArraySimplificationLineNumberTestRunner extends DebugTestBase {

  private static final Class CLASS = ArraySimplificationLineNumberTest.class;
  private static final String FILE = CLASS.getSimpleName() + ".java";
  private static final String NAME = CLASS.getCanonicalName();

  @Test
  @IgnoreIfVmOlderThan(Version.V6_0_1)
  public void testHitOnEntryOnly() throws Throwable {
    DebugTestConfig cf = new CfDebugTestConfig().addPaths(ToolHelper.getClassPathForTests());
    DebugTestConfig d8 = new D8DebugTestConfig().compileAndAdd(
        temp, Collections.singletonList(ToolHelper.getClassFileForTestClass(CLASS)));
    DebugTestConfig d8NoLocals = new D8DebugTestConfig().compileAndAdd(
        temp,
        Collections.singletonList(ToolHelper.getClassFileForTestClass(CLASS)),
        options -> options.testing.noLocalsTableOnInput = true);

    new DebugStreamComparator()
        .add("CF", streamDebugTest(cf, NAME, NO_FILTER))
        .add("D8", streamDebugTest(d8, NAME, ANDROID_FILTER))
        .add("D8/nolocals", streamDebugTest(d8NoLocals, NAME, ANDROID_FILTER))
        .setFilter(s -> s.getSourceFile().equals(FILE))
        .setVerifyVariables(false)
        .compare();
  }
}
