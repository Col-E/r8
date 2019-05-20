// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debuginfo;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.code.Instruction;
import com.android.tools.r8.code.Nop;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.AndroidAppConsumers;
import java.util.Arrays;
import org.junit.Test;

public class Regress111337896TestRunner extends DebugInfoTestBase {

  @Test
  public void test() throws Exception {
    Class clazz = Regress111337896Test.class;

    // Check the regression test is valid code.
    String expected = "aok";
    assertEquals(expected, runOnJava(clazz));

    for (AndroidApiLevel minApi : Arrays.asList(AndroidApiLevel.L, AndroidApiLevel.M)) {
      for (CompilationMode mode : CompilationMode.values()) {
        AndroidAppConsumers appSink = new AndroidAppConsumers();
        D8.run(
            D8Command.builder()
                .addProgramFiles(ToolHelper.getClassFileForTestClass(clazz))
                .setProgramConsumer(appSink.wrapDexIndexedConsumer(null))
                .setMode(mode)
                .setMinApiLevel(minApi.getLevel())
                .build());
        AndroidApp app = appSink.build();
        assertEquals(expected, runOnArt(app, clazz.getCanonicalName()));

        // Check that the compiled output contains a nop to workaround the issue.
        // We can't really check much else as this only reproduces on some physical x86_64 devices.
        check(inspectMethod(app, clazz, "void", "regress111337896"), mode, minApi);
      }
    }
  }

  private void check(DebugInfoInspector info, CompilationMode mode, AndroidApiLevel minApi) {
    info.checkStartLine(12);
    assertEquals(1, info.checkLineExists(18));
    int nopsFound = 0;
    for (Instruction instruction : info.getMethod().getCode().asDexCode().instructions) {
      if (instruction instanceof Nop) {
        nopsFound++;
      }
    }
    if (mode == CompilationMode.DEBUG) {
      // In debug mode a nop is used to preserve line 21.
      info.checkLineExists(21);
      assertEquals(1, nopsFound);
    } else {
      // Release mode will have removed the line.
      info.checkNoLine(21);
      // In release mode the workaround will have inserted a nop if below M.
      int expectedNops = minApi.getLevel() < AndroidApiLevel.M.getLevel() ? 1 : 0;
      assertEquals(expectedNops, nopsFound);
    }
  }

  @Test
  public void testIdenticalNormalAndExceptionalEdge() throws Exception {
    Class clazz = Regress111337896TestIdenticalNormalAndExceptionalEdge.class;

    // Check the regression test is valid code.
    String expected = "aok";
    assertEquals(expected, runOnJava(clazz));

    for (AndroidApiLevel minApi : Arrays.asList(AndroidApiLevel.L, AndroidApiLevel.M)) {
      for (CompilationMode mode : CompilationMode.values()) {
        AndroidAppConsumers appSink = new AndroidAppConsumers();
        D8.run(
            D8Command.builder()
                .addProgramFiles(ToolHelper.getClassFileForTestClass(clazz))
                .setProgramConsumer(appSink.wrapDexIndexedConsumer(null))
                .setMode(mode)
                .setMinApiLevel(minApi.getLevel())
                .build());
        AndroidApp app = appSink.build();
        assertEquals(expected, runOnArt(app, clazz.getCanonicalName()));

        // Check that the compiled output contains a nop to workaround the issue.
        // We can't really check much else as this only reproduces on some physical x86_64 devices.
        checkIdenticalNormalAndExceptionalEdge(
            inspectMethod(app, clazz, "void", "regress111337896"), mode, minApi);
      }
    }
  }

  private void checkIdenticalNormalAndExceptionalEdge(
      DebugInfoInspector info, CompilationMode mode, AndroidApiLevel minApi) {
    info.checkStartLine(11);
    assertEquals(1, info.checkLineExists(13));
    int nopsFound = 0;
    for (Instruction instruction : info.getMethod().getCode().asDexCode().instructions) {
      if (instruction instanceof Nop) {
        nopsFound++;
      }
    }
    if (mode == CompilationMode.DEBUG) {
      assertEquals(0, nopsFound);
    } else {
      // In release mode the workaround will have inserted a nop if below M.
      int expectedNops = minApi.getLevel() < AndroidApiLevel.M.getLevel() ? 1 : 0;
      assertEquals(expectedNops, nopsFound);
    }
  }
}
