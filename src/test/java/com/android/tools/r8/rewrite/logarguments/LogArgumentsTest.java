// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite.logarguments;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.AndroidApp;
import com.google.common.collect.ImmutableList;
import org.junit.Test;

public class LogArgumentsTest extends TestBase {

  private static int occurrences(String match, String value) {
    int count = 0;
    int startIndex = 0;
    while (true) {
      int index = value.indexOf(match, startIndex);
      if (index > 0) {
        count++;
      } else {
        return count;
      }
      startIndex = index + match.length();
    }
  }

  @Test
  public void testStatic() throws Exception {
    String qualifiedMethodName = "com.android.tools.r8.rewrite.logarguments.TestStatic.a";
    String result =
        testForR8(Backend.DEX)
            .addProgramClasses(TestStatic.class)
            .addOptionsModification(
                options -> options.logArgumentsFilter = ImmutableList.of(qualifiedMethodName))
            .assumeAllMethodsMayHaveSideEffects()
            .noMinification()
            .noTreeShaking()
            .run(TestStatic.class)
            .getStdOut();
    assertEquals(7, occurrences(qualifiedMethodName, result));
    assertEquals(3, occurrences("(primitive)", result));
    assertEquals(3, occurrences("(null)", result));
    assertEquals(1, occurrences("java.lang.Object", result));
    assertEquals(1, occurrences("java.lang.Integer", result));
    assertEquals(1, occurrences("java.lang.String", result));
  }

  @Test
  public void testInstance() throws Exception {
    String qualifiedMethodName = "com.android.tools.r8.rewrite.logarguments.TestInstance.a";
    String result =
        testForR8(Backend.DEX)
            .addProgramClasses(TestInstance.class)
            .addOptionsModification(
                options -> options.logArgumentsFilter = ImmutableList.of(qualifiedMethodName))
            .assumeAllMethodsMayHaveSideEffects()
            .noMinification()
            .noTreeShaking()
            .run(TestInstance.class)
            .getStdOut();
    assertEquals(7, occurrences(qualifiedMethodName, result));
    assertEquals(
        7, occurrences("class com.android.tools.r8.rewrite.logarguments.TestInstance", result));
    assertEquals(3, occurrences("(primitive)", result));
    assertEquals(3, occurrences("(null)", result));
    assertEquals(1, occurrences("java.lang.Object", result));
    assertEquals(1, occurrences("java.lang.Integer", result));
    assertEquals(1, occurrences("java.lang.String", result));
  }

  @Test
  public void testInner() throws Exception {
    String qualifiedMethodName = "com.android.tools.r8.rewrite.logarguments.TestInner$Inner.a";
    AndroidApp app = compileWithR8(
        readClasses(TestInner.class, TestInner.Inner.class),
        options -> options.logArgumentsFilter = ImmutableList.of(qualifiedMethodName));
    String result = runOnArt(app, TestInner.class);
    assertEquals(7, occurrences(qualifiedMethodName, result));
    assertEquals(
        7, occurrences("class com.android.tools.r8.rewrite.logarguments.TestInner$Inner", result));
    assertEquals(3, occurrences("(primitive)", result));
    assertEquals(3, occurrences("(null)", result));
    assertEquals(1, occurrences("java.lang.Object", result));
    assertEquals(1, occurrences("java.lang.Integer", result));
    assertEquals(1, occurrences("java.lang.String", result));
  }
}
