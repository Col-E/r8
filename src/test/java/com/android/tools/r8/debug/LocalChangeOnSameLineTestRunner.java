// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class LocalChangeOnSameLineTestRunner extends DebugTestBase {

  private static final Class CLASS = LocalChangeOnSameLineTest.class;
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
    return ImmutableList.of(new Object[]{"CF", cf}, new Object[]{"D8", d8});
  }

  public LocalChangeOnSameLineTestRunner(String name, DelayedDebugTestConfig config) {
    this.name = name;
    this.config = config.getConfig(temp);
  }

  /** Test that only hit the break point at line 15 once. */
  @Test
  public void testHitBreakpointOnce() throws Throwable {
    Assume.assumeFalse("b/73803266",
        name.equals("D8") && ToolHelper.getDexVm() == DexVm.ART_6_0_1_HOST);
    runDebugTest(
        config,
        NAME,
        breakpoint(NAME, "foo", 15),
        run(),
        checkLine(FILE, 15),
        breakpoint(NAME, "main", 21),
        run(),
        checkLine(FILE, 21),
        run());
  }

  /** Test that locals are correct in the frame of foo each time we break in bar. */
  @Test
  public void testLocalsOnBreakpoint() throws Throwable {
    runDebugTest(
        config,
        NAME,
        breakpoint(NAME, "bar"),
        run(),
        checkLine(FILE, 10),
        inspect(t -> t.getFrame(1).checkNoLocal("x")),
        run(),
        checkLine(FILE, 10),
        inspect(t -> t.getFrame(1).checkLocal("x")),
        run(),
        checkLine(FILE, 10),
        inspect(t -> t.getFrame(1).checkNoLocal("x")),
        run(),
        checkLine(FILE, 10),
        inspect(t -> t.getFrame(1).checkLocal("x")),
        run());
  }
}
