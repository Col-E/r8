// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.smali;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.DescriptorUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class DexMoveInstructionsTest extends SmaliTestBase {

  public static final String CLASS = "Test";

  @Test
  public void testValidObjectMoves() throws Throwable {
    ProcessResult result = testMoves("ExpectedToPass", "Ljava/lang/String;", Arrays.asList(
        "move-object",
        "move-object/from16",
        "move-object/16"));
    assertEquals(result.toString(), 0, result.exitCode);
  }

  @Test
  public void testInvalidObjectMoves() throws Throwable {
    ProcessResult result = testMoves("ExpectedToFail", "Ljava/lang/String;", Arrays.asList(
        "move",
        "move/from16",
        "move/16"));
    assertEquals(result.toString(), 1, result.exitCode);
    assertTrue("Did not find 'Verification error' in " + result.stderr,
        result.stderr.contains("Verification error") || result.stderr.contains("VerifyError"));
  }

  @Test
  public void testValidSingleMoves() throws Throwable {
    ProcessResult result = testMoves("ExpectedToPass", "I", Arrays.asList(
        "move",
        "move/from16",
        "move/16"));
    assertEquals(result.toString(), 0, result.exitCode);
  }

  @Test
  public void testInvalidSingleMoves() throws Throwable {
    ProcessResult result = testMoves("ExpectedToFail", "I", Arrays.asList(
        "move-object",
        "move-object/from16",
        "move-object/16"));
    assertEquals(result.toString(), 1, result.exitCode);
    assertTrue("Did not find 'Verification error' in " + result.stderr,
        result.stderr.contains("Verification error") || result.stderr.contains("VerifyError"));
  }

  private ProcessResult testMoves(String clazz, String typeDesc, List<String> moveOps)
      throws Throwable {
    String typeName = DescriptorUtils.descriptorToJavaType(typeDesc);

    SmaliBuilder builder = new SmaliBuilder(clazz);
    int i = 0;
    for (String moveOp : moveOps) {
      builder.addStaticMethod(typeName, "test" + i++, Collections.singletonList(typeName),
          1,
          "    " + moveOp + " v0, p0",
          typeDesc.startsWith("L") ? "return-object v0" : "    return v0"
      );
    }

    List<String> main = new ArrayList<>();
    main.add("  sget-object         v0, Ljava/lang/System;->out:Ljava/io/PrintStream;");
    main.add("  const v2, 0");
    i = 0;
    for (String moveOp : moveOps) {
      main.add("  invoke-static { v2 }, L" + clazz + ";->test" + i++
          + "(" + typeDesc + ")" + typeDesc);
      if (typeDesc.startsWith("L")) {
        main.add("  move-result-object v1");
      } else {
        main.add("  move-result v1");
      }
      main.add("  invoke-virtual { v0, v1 }, Ljava/io/PrintStream;->print(" + typeDesc + ")V");
    }
    main.add("  return-void");
    builder.addMainMethod(3, main.toArray(new String[0]));

    return runOnArtRaw(builder.build(), clazz);
  }
}
