// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.ToolHelper;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.Collections;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class BreakAtTryAndCatchTestRunner extends DebugTestBase {

  private static final Class CLASS = BreakAtTryAndCatchTest.class;
  private static final String FILE = CLASS.getSimpleName() + ".java";
  private static final String NAME = CLASS.getCanonicalName();

  private final String name;
  private final DebugTestConfig config;

  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> setup() {
    DelayedDebugTestConfig cf =
        temp -> new CfDebugTestConfig().addPaths(ToolHelper.getClassPathForTests());
    DelayedDebugTestConfig d8 =
        temp -> new D8DebugTestConfig().compileAndAddClasses(temp, CLASS);
    DelayedDebugTestConfig d8Reordered =
        temp -> new D8DebugTestConfig().compileAndAdd(
            temp,
            Collections.singletonList(ToolHelper.getClassFileForTestClass(CLASS)),
            options -> options.testing.placeExceptionalBlocksLast = true);
    return ImmutableList.of(
        new Object[]{"CF", cf},
        new Object[]{"D8", d8},
        new Object[]{"D8/reorder", d8Reordered}
    );
  }

  public BreakAtTryAndCatchTestRunner(String name, DelayedDebugTestConfig config) {
    this.name = name;
    this.config = config.getConfig(temp);
  }

  @Test
  public void testHitOnEntryOnly() throws Throwable {
    Assume.assumeFalse("b/72933440", name.equals("D8/reorder"));
    runDebugTest(
        config,
        NAME,
        breakpoint(NAME, "foo", 10),
        run(),
        checkLine(FILE, 10), // hit line entry, bar does not throw
        run(),
        checkLine(FILE, 10), // hit line entry, bar does throw
        breakpoint(NAME, "main", 31),
        run(),
        checkLine(FILE, 31), // No more hits on line, continue to main.
        run());
  }
}
