// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.retrace;

import static com.android.tools.r8.naming.retrace.StackTrace.TAB_AT_PREFIX;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.naming.retrace.StackTrace.StackTraceLine;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;

public class StackTraceTest {

  private static String oneLineStackTrace =
      TAB_AT_PREFIX + "Test.main(Test.java:10)" + System.lineSeparator();
  private static String twoLineStackTrace =
      TAB_AT_PREFIX + "Test.a(Test.java:6)" + System.lineSeparator()
          + TAB_AT_PREFIX + "Test.main(Test.java:10)" + System.lineSeparator();

  private void testEquals(String stderr) {
    StackTrace stackTrace = StackTrace.extractFromJvm(stderr);
    assertEquals(stackTrace, stackTrace);
    assertEquals(stackTrace, StackTrace.extractFromJvm(stderr));
  }

  @Test
  public void testOneLine() {
    StackTrace stackTrace = StackTrace.extractFromJvm(oneLineStackTrace);
    assertEquals(1, stackTrace.size());
    StackTraceLine stackTraceLine = stackTrace.get(0);
    assertEquals("Test", stackTraceLine.className);
    assertEquals("main", stackTraceLine.methodName);
    assertEquals("Test.java", stackTraceLine.fileName);
    assertEquals(10, stackTraceLine.lineNumber);
    assertEquals(StringUtils.splitLines(oneLineStackTrace).get(0), stackTraceLine.originalLine);
    assertEquals(oneLineStackTrace, stackTrace.toStringWithPrefix(TAB_AT_PREFIX));
  }

  @Test
  public void testTwoLine() {
    StackTrace stackTrace = StackTrace.extractFromJvm(twoLineStackTrace);
    StackTraceLine stackTraceLine = stackTrace.get(0);
    assertEquals("Test", stackTraceLine.className);
    assertEquals("a", stackTraceLine.methodName);
    assertEquals("Test.java", stackTraceLine.fileName);
    assertEquals(6, stackTraceLine.lineNumber);
    assertEquals(StringUtils.splitLines(twoLineStackTrace).get(0), stackTraceLine.originalLine);
    stackTraceLine = stackTrace.get(1);
    assertEquals("Test", stackTraceLine.className);
    assertEquals("main", stackTraceLine.methodName);
    assertEquals("Test.java", stackTraceLine.fileName);
    assertEquals(10, stackTraceLine.lineNumber);
    assertEquals(StringUtils.splitLines(twoLineStackTrace).get(1), stackTraceLine.originalLine);
    assertEquals(twoLineStackTrace, stackTrace.toStringWithPrefix(TAB_AT_PREFIX));
  }

  @Test
  public void testEqualsOneLine() {
    testEquals(oneLineStackTrace);
  }

  @Test
  public void testEqualsTwoLine() {
    testEquals(twoLineStackTrace);
  }
}
