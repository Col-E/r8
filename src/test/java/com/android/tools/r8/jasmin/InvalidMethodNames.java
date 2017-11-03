// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jasmin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.DexString;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvalidMethodNames extends JasminTestBase {

  public boolean runsOnJVM;
  public String name;

  public InvalidMethodNames(String name, boolean runsOnJVM) {
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
        fail("Invalid dex method names should be compilation errors.");
      }
      String asciiString = new DexString(name).toASCIIString();
      assertTrue(t.getMessage().contains(asciiString));
    } catch (Throwable t) {
      t.printStackTrace(System.out);
      fail("Invalid dex method names should be compilation errors.");
    }
    assertNull("Invalid dex method names should be rejected.", artResult);
  }

  @Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][] {
        { "\u00a0", !ToolHelper.isJava9Runtime()},
        { "\u2000", !ToolHelper.isJava9Runtime()},
        { "\u200f", !ToolHelper.isJava9Runtime()},
        { "\u2028", !ToolHelper.isJava9Runtime()},
        { "\u202f", !ToolHelper.isJava9Runtime()},
        { "\ud800", !ToolHelper.isJava9Runtime()},
        { "\udfff", !ToolHelper.isJava9Runtime()},
        { "\ufff0", !ToolHelper.isJava9Runtime()},
        { "\uffff", !ToolHelper.isJava9Runtime()},
        { "a/b", false },
        { "<a", false },
        { "a>", !ToolHelper.isJava9Runtime() },
        { "<a>", false }
    });
  }

  @Test
  public void invalidMethodName() throws Exception {
    JasminBuilder builder = new JasminBuilder();
    JasminBuilder.ClassBuilder clazz = builder.addClass("Test");

    clazz.addStaticMethod(name, ImmutableList.of(), "V",
        ".limit stack 2",
        ".limit locals 0",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  ldc \"CALLED\"",
        "  invokevirtual java/io/PrintStream.print(Ljava/lang/String;)V",
        "  return");

    clazz.addMainMethod(
        ".limit stack 0",
        ".limit locals 1",
        "  invokestatic Test/" + name + "()V",
        "  return");

    runTest(builder, clazz.name, "CALLED");
  }
}
