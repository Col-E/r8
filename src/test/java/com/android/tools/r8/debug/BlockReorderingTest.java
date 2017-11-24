// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import java.util.Collections;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Test single stepping behaviour across reordered blocks.
 */
public class BlockReorderingTest extends DebugTestBase {

  public static final String CLASS = "BlockReordering";
  public static final String FILE = "BlockReordering.java";

  private static D8DebugTestConfig d8Config;

  @ClassRule
  public static TemporaryFolder temp = ToolHelper.getTemporaryFolderForTest();

  @BeforeClass
  public static void setup() throws Exception {
    // Force inversion of all conditionals to reliably construct a regression test for incorrect
    // line information when reordering blocks.
    d8Config = new D8DebugTestConfig(temp);
    d8Config.compileAndAddPaths(
        temp,
        Collections.singletonList(DEBUGGEE_JAR),
        options -> options.testing.invertConditionals = true);
  }

  @Test
  public void testConditionalReturn() throws Throwable {
    Assume.assumeTrue(
        "Older runtimes incorrectly step out of function: b/67671565",
        ToolHelper.getDexVm().getVersion().isNewerThan(Version.V6_0_1));
    final String method = "conditionalReturn";
    runDebugTest(
        d8Config,
        CLASS,
        breakpoint(CLASS, method),
        run(),
        checkLine(FILE, 8),
        stepOver(),
        checkLine(FILE, 13),
        run(),
        checkLine(FILE, 8),
        stepOver(),
        checkLine(FILE, 9),
        stepOver(),
        checkLine(FILE, 13),
        run());
  }

  @Test
  public void testInvertConditionalReturn() throws Throwable {
    Assume.assumeTrue(
        "Older runtimes incorrectly step out of function: b/67671565",
        ToolHelper.getDexVm().getVersion().isNewerThan(Version.V6_0_1));
    final String method = "invertConditionalReturn";
    runDebugTest(
        d8Config,
        CLASS,
        breakpoint(CLASS, method),
        run(),
        checkLine(FILE, 17),
        stepOver(),
        checkLine(FILE, 18),
        stepOver(),
        checkLine(FILE, 22),
        run(),
        checkLine(FILE, 17),
        stepOver(),
        checkLine(FILE, 22),
        run());
  }

  @Test
  public void testFallthroughReturn() throws Throwable {
    Assume.assumeTrue(
        "Older runtimes incorrectly step out of function: b/67671565",
        ToolHelper.getDexVm().getVersion().isNewerThan(Version.V6_0_1));
    final String method = "fallthroughReturn";
    runDebugTest(
        d8Config,
        CLASS,
        breakpoint(CLASS, method),
        run(),
        checkLine(FILE, 26),
        stepOver(),
        checkLine(FILE, 35),
        run(),
        checkLine(FILE, 26),
        stepOver(),
        checkLine(FILE, 30),
        stepOver(),
        checkLine(FILE, 35),
        run(),
        checkLine(FILE, 26),
        stepOver(),
        checkLine(FILE, 31),
        stepOver(),
        checkLine(FILE, 35),
        run());
  }
}
