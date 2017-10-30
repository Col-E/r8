// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.code.IfEqz;
import com.android.tools.r8.code.Instruction;
import com.android.tools.r8.debuginfo.DebugInfoInspector;
import com.android.tools.r8.graph.DexDebugEntry;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.smali.SmaliTestBase.SmaliBuilder;
import com.android.tools.r8.utils.AndroidApp;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.apache.harmony.jpda.tests.framework.jdwp.Value;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SmaliDebugTest extends DebugTestBase {

  static final String FILE = "SmaliDebugTestDebuggee.smali";
  static final String CLASS = "SmaliDebugTestDebuggee";

  @Rule
  public TemporaryFolder temp = ToolHelper.getTemporaryFolderForTest();

  /**
   * Simple test to check setup works for the Java source, ala:
   *
   * static int method(int x) {
   *   if (x != 0) {
   *     return x
   *   } else {
   *     return 42;
   *   }
   * }
   */
  @Test
  public void testSimpleIf() throws Throwable {
    String methodName = "simpleIf";
    runDebugTest(buildSimpleIf(methodName), CLASS,
        breakpoint(CLASS, methodName),
        run(),
        // first call x == 0
        checkLine(FILE, 1),
        checkLocal("x", Value.createInt(0)),
        stepOver(),
        checkLine(FILE, 4),
        run(),
        // second call x == 1
        checkLine(FILE, 1),
        checkLocal("x", Value.createInt(1)),
        stepOver(),
        checkLine(FILE, 2),
        run());
  }

  private List<Path> buildSimpleIf(String methodName)
      throws Throwable {
    SmaliBuilder builder = new SmaliBuilder();
    builder.addClass(CLASS);
    builder.setSourceFile(FILE);
    builder.addStaticMethod("int", methodName, Collections.singletonList("int"), 0,
        ".param p0, \"x\"    # I",
        ".line 1",
        "    if-eqz p0, :onzero",
        ".line 2",
        "    return p0",
        ":onzero",
        ".line 4",
        "    const p0, 42",
        "    return p0");

    builder.addMainMethod(2,
        "    const v0, 0",
        "    invoke-static       { v0 }, L" + CLASS + ";->" + methodName + "(I)I",
        "    move-result         v1",
        "    sget-object         v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "    invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->print(I)V",
        "    const v0, 1",
        "    invoke-static       { v0 }, L" + CLASS + ";->" + methodName + "(I)I",
        "    move-result         v1",
        "    sget-object         v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "    invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->print(I)V",
        "    return-void"
    );
    return buildAndRun(builder);
  }

  /**
   * Test that a jump to a position that happens after a "default" debug event still triggers a
   * break point due to the line change.
   */
  @Test
  public void testJumpAfterLineChange() throws Throwable {
    String methodName = "jumpAfterLineChange";
    List<Path> outs = buildJumpAfterLineChange(methodName);

    // Verify that the PC associated with the line entry 4 is prior to the target of the condition.
    DebugInfoInspector info = new DebugInfoInspector(AndroidApp.fromProgramFiles(outs), CLASS,
        new MethodSignature(methodName, "int", new String[]{ "int" }));
    IfEqz cond = null;
    for (Instruction instruction : info.getMethod().getCode().asDexCode().instructions) {
      if (instruction.getOpcode() == IfEqz.OPCODE) {
        cond = (IfEqz) instruction;
        break;
      }
    }
    assertNotNull(cond);
    int target = cond.getTargets()[0] + cond.getOffset();
    int linePC = -1;
    for (DexDebugEntry entry : info.getEntries()) {
      if (entry.line == 4) {
        linePC = entry.address;
        break;
      }
    }
    assertTrue(linePC > -1);
    assertTrue(target > linePC);

    // Run debugger to verify that we step to line 4 and the values of v0 and v1 are unchanged.
    runDebugTest(outs, CLASS,
        breakpoint(CLASS, methodName),
        run(),
        // first call x == 0
        checkLine(FILE, 1),
        checkLocal("x", Value.createInt(0)),
        checkNoLocal("v0"),
        checkNoLocal("v1"),
        stepOver(),
        checkLine(FILE, 4),
        checkLocal("v0", Value.createInt(0)),
        checkLocal("v1", Value.createInt(7)),
        stepOver(),
        checkLine(FILE, 5),
        checkLocal("v0", Value.createInt(42)),
        checkLocal("v1", Value.createInt(7)),
        run(),
        // second call x == 1
        checkLine(FILE, 1),
        checkLocal("x", Value.createInt(1)),
        checkNoLocal("v0"),
        checkNoLocal("v1"),
        stepOver(),
        checkLine(FILE, 2),
        checkNoLocal("v0"),
        checkNoLocal("v1"),
        run());
  }

  private List<Path> buildJumpAfterLineChange(String methodName)
      throws Throwable {
    SmaliBuilder builder = new SmaliBuilder();
    builder.addClass(CLASS);
    builder.setSourceFile(FILE);
    builder.addStaticMethod("int", methodName, Collections.singletonList("int"), 2,
        ".param p0, \"x\"    # I",
        ".line 1",
        "    const v0, 0",
        "    const v1, 7",
        "    if-eqz p0, :onzero",
        ".line 2",
        "    return p0",
        ".line 4",
        ".local v0, \"v0\":I",
        ".local v1, \"v1\":I",
        "    const v0, 1234", // odd and unreachable code
        "    const v1, 5678", // ...
        ":onzero",
        "    const v0, 42",
        ".line 5",
        "    return v0");

    builder.addMainMethod(2,
        "    const v0, 0",
        "    invoke-static       { v0 }, L" + CLASS + ";->" + methodName + "(I)I",
        "    move-result         v1",
        "    sget-object         v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "    invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->print(I)V",
        "    const v0, 1",
        "    invoke-static       { v0 }, L" + CLASS + ";->" + methodName + "(I)I",
        "    move-result         v1",
        "    sget-object         v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "    invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->print(I)V",
        "    return-void"
    );
    return buildAndRun(builder);
  }

  private List<Path> buildAndRun(SmaliBuilder builder) throws Throwable {
    byte[] bytes = builder.compile();
    Path out = temp.getRoot().toPath().resolve("classes.dex");
    Files.write(out, bytes);
    ToolHelper.runArtNoVerificationErrors(out.toString(), CLASS);
    return Collections.singletonList(out);
  }
}
