// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debuginfo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.code.AddInt;
import com.android.tools.r8.code.AddInt2Addr;
import com.android.tools.r8.code.Instruction;
import com.android.tools.r8.code.Return;
import com.android.tools.r8.utils.AndroidApp;
import org.junit.Test;

public class CodeGeneratorTestRunner extends DebugInfoTestBase {

  /**
   * Companion test checking the behavior when attached to a debugger
   * {@link com.android.tools.r8.debug.LocalsTest#testLocalUsedBy2AddrInstruction}
   */
  @Test
  public void test2AddrInstruction() throws Exception {
    Class clazz = CodeGeneratorTest.class;

    AndroidApp d8App = compileWithD8(clazz);
    AndroidApp dxApp = getDxCompiledSources();

    String expected = "11";
    assertEquals(expected, runOnJava(clazz));
    assertEquals(expected, runOnArt(d8App, clazz.getCanonicalName()));
    assertEquals(expected, runOnArt(dxApp, clazz.getCanonicalName()));

    DebugInfoInspector inspector = inspectMethod(d8App, clazz, "int", "intAddition", "int", "int",
        "int");
    Instruction[] instructions = inspector.getMethod().getCode().asDexCode().instructions;
    assertTrue(instructions[0] instanceof AddInt2Addr);
    assertTrue(instructions[1] instanceof AddInt2Addr);
    assertTrue(instructions[2] instanceof AddInt);
    assertTrue(instructions[3] instanceof Return);
  }

}
