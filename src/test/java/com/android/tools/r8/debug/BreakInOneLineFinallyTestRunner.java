// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.ToolHelper;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class BreakInOneLineFinallyTestRunner extends DebugTestBase {

  private static final Class CLASS = BreakInOneLineFinallyTest.class;
  private static final String FILE = CLASS.getSimpleName() + ".java";
  private static final String NAME = CLASS.getCanonicalName();

  private final DebugTestConfig config;

  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> setup() {
    DelayedDebugTestConfig cf =
            temp -> new CfDebugTestConfig().addPaths(ToolHelper.getClassPathForTests());
    DelayedDebugTestConfig d8 =
            temp -> new D8DebugTestConfig().compileAndAddClasses(temp, CLASS);
    return ImmutableList.of(new Object[]{"CF", cf}, new Object[]{"D8", d8});
  }

  public BreakInOneLineFinallyTestRunner(String name, DelayedDebugTestConfig config) {
    this.config = config.getConfig(temp);
  }

  @Test
  public void testHitBreakpointOnNormalAndExceptionalFlow() throws Throwable {
    Assume.assumeFalse(
        "b/72933440 : JavaC doesn't duplicate line-table entries when duplicating finally blocks",
        config instanceof D8DebugTestConfig);
    runDebugTest(
        config,
        NAME,
        breakpoint(NAME, "foo", 11),
        run(),
        checkLine(FILE, 11), // hit finally on normal flow
        breakpoint(NAME, "main", 34), // can't hit the exceptional block :-(
        run(),
        checkLine(FILE, 34),
        run());
  }
}
