// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.retraceproguard;

import static com.android.tools.r8.naming.retraceproguard.StackTrace.TAB_AT_PREFIX;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.naming.retraceproguard.StackTrace.StackTraceLine;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;

public class StackTraceTest {

  private static String lineOne =
      TAB_AT_PREFIX + "Test.main(Test.java:10)" + System.lineSeparator();

  private static String lineTwo = TAB_AT_PREFIX + "Test.a(Test.java:6)" + System.lineSeparator();

  private static String randomArtLine =
      "art W 26343 26343 art/runtime/base/mutex.cc:694] ConditionVariable::~ConditionVariable "
          + "for Thread resumption condition variable called with 1 waiters."
          + System.lineSeparator();

  private static String oneLineStackTrace = lineOne;
  private static String twoLineStackTrace = lineTwo + lineOne;

  private void testEquals(String stderr) {
    StackTrace stackTrace = StackTrace.extractFromJvm(stderr);
    assertEquals(stackTrace, stackTrace);
    assertEquals(stackTrace, StackTrace.extractFromJvm(stderr));
  }

  private void checkOneLine(StackTrace stackTrace) {
    assertEquals(1, stackTrace.size());
    StackTraceLine stackTraceLine = stackTrace.get(0);
    assertEquals("Test", stackTraceLine.className);
    assertEquals("main", stackTraceLine.methodName);
    assertEquals("Test.java", stackTraceLine.fileName);
    assertEquals(10, stackTraceLine.lineNumber);
    assertEquals(StringUtils.splitLines(oneLineStackTrace).get(0), stackTraceLine.originalLine);
    assertEquals(oneLineStackTrace, stackTrace.toStringWithPrefix(TAB_AT_PREFIX));
  }

  private void checkTwoLines(StackTrace stackTrace) {
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
  public void testOneLineJvm() {
    checkOneLine(StackTrace.extractFromJvm(oneLineStackTrace));
  }

  @Test
  public void testOneLineArt() {
    checkOneLine(StackTrace.extractFromArt(oneLineStackTrace));
    checkOneLine(StackTrace.extractFromArt(oneLineStackTrace + randomArtLine));
    checkOneLine(StackTrace.extractFromArt(randomArtLine + oneLineStackTrace));
  }

  @Test
  public void testTwoLinesJvm() {
    checkTwoLines(StackTrace.extractFromJvm(twoLineStackTrace));
  }

  @Test
  public void testTwoLinesArt() {
    checkTwoLines(StackTrace.extractFromJvm(twoLineStackTrace));
    checkTwoLines(StackTrace.extractFromJvm(twoLineStackTrace + randomArtLine));
    checkTwoLines(StackTrace.extractFromJvm(randomArtLine + twoLineStackTrace));
    checkTwoLines(StackTrace.extractFromJvm(lineTwo + randomArtLine + lineOne));
  }

  @Test
  public void testEqualsOneLine() {
    testEquals(lineOne);
  }

  @Test
  public void testEqualsTwoLine() {
    testEquals(twoLineStackTrace);
  }
}
