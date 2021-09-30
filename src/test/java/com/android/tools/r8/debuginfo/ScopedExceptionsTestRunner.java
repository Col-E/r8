// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debuginfo;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.utils.AndroidApp;
import org.junit.Test;

public class ScopedExceptionsTestRunner extends DebugInfoTestBase {

  @Test
  public void testScopedException() throws Exception {
    Class clazz = ScopedExceptionsTest.class;
    AndroidApp d8App = compileWithD8(clazz);

    String expected = "42";
    assertEquals(expected, runOnJava(clazz));
    assertEquals(expected, runOnArt(d8App, clazz.getCanonicalName()));

    checkScopedExceptions(inspectMethod(d8App, clazz, "int", "scopedExceptions"));
  }

  private void checkScopedExceptions(DebugInfoInspector info) {
    info.checkStartLine(10);
    info.checkLineHasNoLocals(10);
    info.checkNoLine(11);
    info.checkLineHasNoLocals(12);
    info.checkLineHasNoLocals(13);
    info.checkLineHasExactLocals(14, "e", "java.lang.Throwable");
    info.checkLineHasNoLocals(15);
    info.checkLineExists(16);
    info.checkLineHasNoLocals(16);
  }
}
