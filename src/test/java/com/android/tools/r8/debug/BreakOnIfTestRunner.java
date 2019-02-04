// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.ToolHelper;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class BreakOnIfTestRunner extends DebugTestBase {

  private static final Class CLASS = BreakOnIfTest.class;
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

  public BreakOnIfTestRunner(String name, DelayedDebugTestConfig config) {
    this.config = config.getConfig(temp);
  }

  @Test
  public void test() throws Throwable {
    runDebugTest(
        config,
        NAME,
        breakpoint(NAME, "main", 9),
        run(),
        checkLine(FILE, 9),
        stepOver(),
        checkLine(FILE, 12),
        run());
  }
}
