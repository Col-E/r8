// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debuginfo;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.AndroidAppConsumers;
import com.android.tools.r8.utils.StringUtils;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import org.junit.Test;

public class SynchronizedMethodTestRunner extends DebugInfoTestBase {

  static Class clazz = SynchronizedMethodTest.class;

  @Test
  public void testSynchronizedMethod() throws Exception {
    AndroidApp d8App = compileWithD8(clazz);
    AndroidApp dxApp = getDxCompiledSources();

    String expected = StringUtils.lines("42", "42", "2", "2");
    assertEquals(expected, runOnJava(clazz));
    assertEquals(expected, runOnArt(d8App, clazz.getCanonicalName()));
    assertEquals(expected, runOnArt(dxApp, clazz.getCanonicalName()));

    checkSyncStatic(inspectMethod(d8App, clazz, "int", "syncStatic", "int"));
    checkSyncStatic(inspectMethod(dxApp, clazz, "int", "syncStatic", "int"));

    checkSyncInstance(inspectMethod(d8App, clazz, "int", "syncInstance", "int"));
    checkSyncInstance(inspectMethod(dxApp, clazz, "int", "syncInstance", "int"));

    checkThrowing(inspectMethod(d8App, clazz, "int", "throwing", "int"), false);
    checkThrowing(inspectMethod(dxApp, clazz, "int", "throwing", "int"), true);

    checkMonitorExitRegression(
        inspectMethod(d8App, clazz, "int", "monitorExitRegression", "int"), false);
    checkMonitorExitRegression(
        inspectMethod(dxApp, clazz, "int", "monitorExitRegression", "int"), true);
  }

  private void checkSyncStatic(DebugInfoInspector info) {
    info.checkStartLine(9);
    info.checkLineHasExactLocals(9, "x", "int");
    info.checkLineHasExactLocals(10, "x", "int");
    info.checkNoLine(11);
    info.checkLineHasExactLocals(12, "x", "int");
    info.checkNoLine(13);
  }

  private void checkSyncInstance(DebugInfoInspector info) {
    String[] locals = {"this", clazz.getCanonicalName(), "x", "int"};
    info.checkStartLine(16);
    info.checkLineHasExactLocals(16, locals);
    info.checkLineHasExactLocals(17, locals);
    info.checkNoLine(18);
    info.checkLineHasExactLocals(19, locals);
    info.checkNoLine(20);
  }

  private void checkThrowing(DebugInfoInspector info, boolean dx) {
    info.checkStartLine(23);
    if (!dx) {
      info.checkLineHasExactLocals(23, "cond", "int");
    }
    info.checkLineHasExactLocals(24, "cond", "int", "x", "int");
    info.checkLineHasExactLocals(25, "cond", "int", "x", "int");
    info.checkNoLine(26);
    info.checkLineHasExactLocals(27, "cond", "int", "x", "int");
  }

  private void checkMonitorExitRegression(DebugInfoInspector info, boolean dx) {
    info.checkStartLine(31);
    for (int line : Arrays.asList(32, 34, 36, 38, 40, 42, 44, 48, 50, 52)) {
      if (dx && line == 40) {
        continue;
      }
      info.checkLineHasExactLocals(line, "cond", "int", "x", "int");
    }
  }

  @Test
  public void testMonitorExitLineInRelease()
      throws CompilationFailedException, IOException, ExecutionException {
    AndroidAppConsumers sink = new AndroidAppConsumers();
    D8.run(D8Command.builder()
        .addProgramFiles(ToolHelper.getClassFileForTestClass(clazz))
        .setMode(CompilationMode.RELEASE)
        .setProgramConsumer(sink.wrapDexIndexedConsumer(null))
        .build());
    AndroidApp app = sink.build();
    DebugInfoInspector inspector = inspectMethod(app, clazz, "int", "syncStatic", "int");
    // The first line of syncStatic is 9 and thus the synthetic exit for the exceptional case will
    // have line number 8. In a release build we want to ensure that the synthetic exit does not
    // have an associated line.
    inspector.checkStartLine(9);
    inspector.checkNoLine(8);
    // Also ensure we did not emit a preamble position at line zero.
    inspector.checkNoLine(0);
  }
}
