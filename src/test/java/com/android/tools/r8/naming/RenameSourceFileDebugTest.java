// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import com.android.tools.r8.debug.DebugTestBase;
import com.android.tools.r8.shaking.ProguardKeepRule;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests -renamesourcefileattribute.
 */
public class RenameSourceFileDebugTest extends DebugTestBase {

  private static final String TEST_FILE = "TestFile.java";

  @BeforeClass
  public static void setUp() throws Exception {
    setUp(null, pg -> {
      pg.addRule(ProguardKeepRule.defaultKeepAllRule());
      pg.setRenameSourceFileAttribute(TEST_FILE);
    });
  }

  /**
   * replica of {@link ClassInitializationTest#testBreakpointInEmptyClassInitializer}
   */
  @Test
  public void testBreakpointInEmptyClassInitializer() throws Throwable {
    final String CLASS = "ClassInitializerEmpty";
    runDebugTestR8(CLASS,
        breakpoint(CLASS, "<clinit>"),
        run(),
        checkLine(TEST_FILE, 8),
        run());
  }

  /**
   * replica of {@link LocalsTest#testNoLocal}, except for checking overwritten class file.
   */
  @Test
  public void testNoLocal() throws Throwable {
    final String className = "Locals";
    final String methodName = "noLocals";
    runDebugTestR8(className,
        breakpoint(className, methodName),
        run(),
        checkMethod(className, methodName),
        checkLine(TEST_FILE, 8),
        checkNoLocal(),
        stepOver(),
        checkMethod(className, methodName),
        checkLine(TEST_FILE, 9),
        checkNoLocal(),
        run());
  }

  /**
   * replica of {@link MultipleReturnsTest#testMultipleReturns}
   */
  @Test
  public void testMultipleReturns() throws Throwable {
    runDebugTestR8("MultipleReturns",
        breakpoint("MultipleReturns", "multipleReturns"),
        run(),
        stepOver(),
        checkLine(TEST_FILE, 16), // this should be the 1st return statement
        run(),
        stepOver(),
        checkLine(TEST_FILE, 18), // this should be the 2nd return statement
        run());
  }
}
