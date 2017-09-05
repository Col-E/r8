// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debuginfo;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.utils.AndroidApp;
import org.junit.Test;

public class LiveInAllBlocksTestRunner extends DebugInfoTestBase {

  // Regression test for b/65272487.
  @Test
  public void testLiveInAllBlocks() throws Exception {
    Class clazz = LiveInAllBlocksTest.class;
    AndroidApp d8App = compileWithD8(clazz);
    AndroidApp dxApp = getDxCompiledSources();

    String expected = "42";
    assertEquals(expected, runOnJava(clazz));
    assertEquals(expected, runOnArt(d8App, clazz.getCanonicalName()));
    assertEquals(expected, runOnArt(dxApp, clazz.getCanonicalName()));

    checkFoo(inspectMethod(d8App, clazz, "int", "foo", "int"), false);
    checkFoo(inspectMethod(dxApp, clazz, "int", "foo", "int"), true);
  }

  private void checkFoo(DebugInfoInspector info, boolean dx) {
    info.checkStartLine(9);
    for (int line : new int[] {14, 18, 23, 24, 25, 27, 28, 30, 31, 34, 35, 37, 38, 40, 41}) {
      if (dx && line == 18) {
        // DX does not keep entry for line 18.
        continue;
      }
      info.checkLineHasAtLeastLocals(line, "x", "int", "y", "int");
    }
  }
}
