// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DoNotCrashOnAccessToThisRunner extends DebugTestBase {

  private static final Class CLASS = DoNotCrashOnAccessToThis.class;
  private static final String FILE = CLASS.getSimpleName() + ".java";
  private static final String NAME = CLASS.getCanonicalName();

  private final DebugTestConfig config;

  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> setup() {
    DelayedDebugTestConfig cf =
        temp -> new CfDebugTestConfig().addPaths(ToolHelper.getClassPathForTests());
    DelayedDebugTestConfig d8 =
        temp -> new D8DebugTestConfig().compileAndAdd(
            temp,
            ImmutableList.of(ToolHelper.getClassFileForTestClass(CLASS)),
            options -> {
              // Release mode so receiver can be clobbered.
              options.debug = false;
              // Api level M so that the workarounds for Lollipop verifier doesn't
              // block the receiver register. We want to check b/116683601 which
              // happens on at least 7.0.0.
              options.minApiLevel = AndroidApiLevel.M.getLevel();
            });
    return ImmutableList.of(new Object[]{"CF", cf}, new Object[]{"D8", d8});
  }

  public DoNotCrashOnAccessToThisRunner(String name, DelayedDebugTestConfig config) {
    this.config = config.getConfig(temp);
  }

  @Test
  public void doNotCrash() throws Throwable {
    Assume.assumeFalse(ToolHelper.getDexVm().isOlderThanOrEqual(DexVm.ART_6_0_1_HOST));
    runDebugTest(
        config,
        NAME,
        breakOnException(NAME, "mightClobberThis", true, false),
        run(),
        checkLine(FILE, 21),
        checkLocal("this"),
        run());
  }
}
