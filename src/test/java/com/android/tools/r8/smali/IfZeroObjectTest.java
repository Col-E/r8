// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.smali;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ToolHelper.ProcessResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class IfZeroObjectTest extends SmaliTestBase {

  public static final String CLASS = "Test";

  @Test
  public void testObjectIfs() throws Throwable {
    ProcessResult result = testIfs("ExpectedToPass", Arrays.asList(
        "eqz",
        "nez"));
    assertEquals(result.toString(), 0, result.exitCode);
  }

  @Test
  public void testNumericIfs() throws Throwable {
    ProcessResult result = testIfs("ExpectedToFail", Arrays.asList(
        "ltz",
        "gez",
        "gtz",
        "lez"));
    assertEquals(result.toString(), 1, result.exitCode);
    assertTrue("Did not find 'Verification error' in " + result.stderr,
        result.stderr.contains("Verification error") || result.stderr.contains("VerifyError"));
  }

  private ProcessResult testIfs(String clazz, List<String> ifZeroOps) throws Throwable {

    SmaliBuilder builder = new SmaliBuilder(clazz);
    for (String ifZeroOp : ifZeroOps) {
      builder.addStaticMethod("int", "if" + ifZeroOp, Collections.singletonList("java.lang.Object"),
          1,
          "    if-" + ifZeroOp + " p0, :L",
          "    const v0, 0",
          "    return v0",
          "  :L",
          "    const v0, 1",
          "    return v0"
      );
    }

    List<String> main = new ArrayList<>();
    main.add("  sget-object         v0, Ljava/lang/System;->out:Ljava/io/PrintStream;");
    for (String ifZeroOp : ifZeroOps) {
      main.add("  invoke-static { p0 }, L" + clazz + ";->if" + ifZeroOp + "(Ljava/lang/Object;)I");
      main.add("  move-result v1");
      main.add("  invoke-virtual { v0, v1 }, Ljava/io/PrintStream;->print(I)V");
    }
    main.add("  return-void");
    builder.addMainMethod(2, main.toArray(new String[0]));

    return runOnArtRaw(builder.build(), clazz);
  }
}
