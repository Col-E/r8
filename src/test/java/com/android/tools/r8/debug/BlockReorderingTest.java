// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Test single stepping behaviour across reordered blocks.
 */
public class BlockReorderingTest extends DebugTestBase {

  public static final String CLASS = "BlockReordering";
  public static final String FILE = "BlockReordering.java";

  @BeforeClass
  public static void setUp() throws Exception {
    // Force inversion of all conditionals to reliably construct a regression test for incorrect
    // line information when reording blocks.
    setUp(options -> options.testing.invertConditionals = true, null);
  }

  @Test
  @Ignore("b/65618023")
  public void testConditionalReturn() throws Throwable {
    final String method = "conditionalReturn";
    runDebugTest(CLASS,
        breakpoint(CLASS, method),
        run(),
        checkLine(FILE, 8), stepOver(),
        checkLine(FILE, 13),
        run(),
        checkLine(FILE, 8), stepOver(),
        checkLine(FILE, 9), stepOver(),
        checkLine(FILE, 13),
        run());
  }

  @Test
  @Ignore("b/65618023")
  public void testInvertConditionalReturn() throws Throwable {
    final String method = "invertConditionalReturn";
    runDebugTest(CLASS,
        breakpoint(CLASS, method),
        run(),
        checkLine(FILE, 17), stepOver(),
        checkLine(FILE, 18), stepOver(),
        checkLine(FILE, 22),
        run(),
        checkLine(FILE, 17), stepOver(),
        checkLine(FILE, 22),
        run());
  }

  @Test
  @Ignore("b/65618023")
  public void testFallthroughReturn() throws Throwable {
    final String method = "fallthroughReturn";
    runDebugTest(CLASS,
        breakpoint(CLASS, method),
        run(),
        checkLine(FILE, 26), stepOver(),
        checkLine(FILE, 35),
        run(),
        checkLine(FILE, 26), stepOver(),
        checkLine(FILE, 30), stepOver(),
        checkLine(FILE, 35),
        run(),
        checkLine(FILE, 26), stepOver(),
        checkLine(FILE, 31), stepOver(),
        checkLine(FILE, 35),
        run());
  }
}
