// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jasmin;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.errors.CompilationError;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvalidClassNames extends JasminTestBase {

  public boolean runsOnJVM;
  public String name;

  public InvalidClassNames(String name, boolean runsOnJVM) {
    this.name = name;
    this.runsOnJVM = runsOnJVM;
  }

  private void runTest(JasminBuilder builder, String main, String expected) throws Exception {
    if (runsOnJVM) {
      String javaResult = runOnJava(builder, main);
      assertEquals(expected, javaResult);
    }
    String artResult = null;
    try {
      artResult = runOnArt(builder, main);
      fail();
    } catch (ExecutionException t) {
      if (!(t.getCause() instanceof CompilationError)) {
        t.printStackTrace(System.out);
        fail("Invalid dex class names should be compilation errors.");
      }
    } catch (Throwable t) {
      t.printStackTrace(System.out);
      fail("Invalid dex class names should be compilation errors.");
    }
    assertNull("Invalid dex class names should be rejected.", artResult);
  }

  @Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][] {
        { "\u00a0", !ToolHelper.isJava9Runtime()},
        { "\u2000", !ToolHelper.isWindows() && !ToolHelper.isJava9Runtime()},
        { "\u200f", !ToolHelper.isWindows() && !ToolHelper.isJava9Runtime()},
        { "\u2028", !ToolHelper.isWindows() && !ToolHelper.isJava9Runtime()},
        { "\u202f", !ToolHelper.isWindows() && !ToolHelper.isJava9Runtime()},
        { "\ud800", false},
        { "\udfff", false},
        { "\ufff0", !ToolHelper.isWindows() && !ToolHelper.isJava9Runtime()},
        { "\uffff", !ToolHelper.isWindows() && !ToolHelper.isJava9Runtime()},
        { "a/b/c/a/D/", true },
        { "a<b", !ToolHelper.isWindows() && !ToolHelper.isJava9Runtime()},
        { "a>b", !ToolHelper.isWindows() && !ToolHelper.isJava9Runtime()},
        { "<a>b", !ToolHelper.isWindows() && !ToolHelper.isJava9Runtime()},
        { "<a>", !ToolHelper.isWindows() && !ToolHelper.isJava9Runtime()}
    });
  }

  @Test
  public void invalidClassName() throws Exception {
    JasminBuilder builder = new JasminBuilder();
    JasminBuilder.ClassBuilder clazz = builder.addClass(name);
    clazz.addMainMethod(
        ".limit stack 2",
        ".limit locals 1",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  ldc \"MAIN\"",
        "  invokevirtual java/io/PrintStream.print(Ljava/lang/String;)V",
        "  return");

    runTest(builder, clazz.name, "MAIN");
  }
}
