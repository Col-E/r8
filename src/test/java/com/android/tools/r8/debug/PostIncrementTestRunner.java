// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.debug.DebugTestBase.JUnit3Wrapper.DebuggeeState;
import java.util.stream.Stream;
import org.junit.Assume;
import org.junit.Test;

// See b/80385846
public class PostIncrementTestRunner extends DebugTestBase {

  private static final Class CLASS = PostIncrementTest.class;
  private static final String NAME = CLASS.getCanonicalName();

  @Test
  public void test() throws Exception {
    Assume.assumeTrue("Older runtimes cause some kind of debug streaming issues",
        ToolHelper.getDexVm().isNewerThan(DexVm.ART_5_1_1_HOST));
    DebugTestConfig cfConfig = new CfDebugTestConfig().addPaths(ToolHelper.getClassPathForTests());
    DebugTestConfig d8Config = new D8DebugTestConfig().compileAndAddClasses(temp, CLASS);
    new DebugStreamComparator()
        .add("CF", createStream(cfConfig))
        .add("D8", createStream(d8Config))
        .compare();
  }

  private Stream<DebuggeeState> createStream(DebugTestConfig config) throws Exception {
    return streamDebugTest(config, NAME, ANDROID_FILTER);
  }
}
