// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.jasmin.JasminBuilder.ClassBuilder;
import com.android.tools.r8.jasmin.JasminTestBase;
import com.android.tools.r8.utils.DefaultDiagnosticsHandler;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import org.junit.Assert;
import org.junit.Test;

public class NullArrayAndNullObjectValueTest extends JasminTestBase {

  @Test
  public void testNullIsArray() throws Exception {
    JasminBuilder jasminBuilder = new JasminBuilder();
    ClassBuilder classBuilder = jasminBuilder.addClass("TestClass");

    // Store directly to the null value.
    classBuilder.addStaticMethod(
        "nullArrayStore",
        ImmutableList.of(),
        "V",
        ".limit stack 10",
        ".limit locals 10",
        ".catch java/lang/NullPointerException from L0 to L1 using L2",
        "aconst_null",
        "iconst_0",
        "iconst_1",
        "L0:",
        "iastore",
        "L1:",
        "return",
        "L2:",
        "pop",
        "getstatic java/lang/System/out Ljava/io/PrintStream;",
        "ldc \"NPE\"",
        "invokevirtual java/io/PrintStream/println(Ljava/lang/String;)V",
        "return");

    // Load directly from the null value.
    classBuilder.addStaticMethod(
        "nullArrayLoad",
        ImmutableList.of(),
        "V",
        ".limit stack 10",
        ".limit locals 10",
        ".catch java/lang/NullPointerException from L0 to L1 using L2",
        "aconst_null",
        "iconst_0",
        "L0:",
        "iaload",
        "L1:",
        "pop",
        "return",
        "L2:",
        "pop",
        "getstatic java/lang/System/out Ljava/io/PrintStream;",
        "ldc \"NPE\"",
        "invokevirtual java/io/PrintStream/println(Ljava/lang/String;)V",
        "return");

    // Get the length of the null value.
    classBuilder.addStaticMethod(
        "nullArrayLength",
        ImmutableList.of(),
        "V",
        ".limit stack 10",
        ".limit locals 10",
        ".catch java/lang/NullPointerException from L0 to L1 using L2",
        "aconst_null",
        "L0:",
        "arraylength",
        "L1:",
        "pop",
        "return",
        "L2:",
        "pop",
        "getstatic java/lang/System/out Ljava/io/PrintStream;",
        "ldc \"NPE\"",
        "invokevirtual java/io/PrintStream/println(Ljava/lang/String;)V",
        "return");

    // Use the same null value (local containing null) as both an object type and an array type.
    classBuilder.addStaticMethod(
        "nullToObjectAndArray",
        ImmutableList.of(),
        "V",
        ".limit stack 10",
        ".limit locals 10",
        // Stored null in local 0
        "aconst_null",
        "astore 0",
        // Create single-cell array of object type and store the null in it.
        "iconst_1",
        "anewarray java/lang/String",
        "iconst_0",
        "aload 0",
        "aastore",
        // Create single-cell array of array type and store the null in it.
        "iconst_1",
        "anewarray [I",
        "iconst_0",
        "aload 0",
        "aastore",
        "return");

    classBuilder.addMainMethod(
        ".limit stack 5",
        ".limit locals 5",
        "invokestatic TestClass/nullToObjectAndArray()V",
        "invokestatic TestClass/nullArrayStore()V",
        "invokestatic TestClass/nullArrayLoad()V",
        "invokestatic TestClass/nullArrayLength()V",
        "return");

    Path riJar = temp.getRoot().toPath().resolve("ri-out.jar");
    jasminBuilder.writeJar(riJar, new DefaultDiagnosticsHandler());
    ProcessResult riResult = ToolHelper.runJava(riJar, "TestClass");
    Assert.assertEquals(riResult.toString(), 0, riResult.exitCode);

    Path d8Jar = temp.getRoot().toPath().resolve("d8-out.jar");
    D8.run(
        D8Command.builder().addProgramFiles(riJar).setOutput(d8Jar, OutputMode.DexIndexed).build());
    ProcessResult d8Result = ToolHelper.runArtRaw(d8Jar.toString(), "TestClass");
    Assert.assertEquals(d8Result.toString(), 0, riResult.exitCode);

    Assert.assertEquals(riResult.stdout, d8Result.stdout);
  }

  @Test
  public void testObjectNotArray() throws Exception {
    JasminBuilder jasminBuilder = new JasminBuilder();
    ClassBuilder classBuilder = jasminBuilder.addClass("TestClass");

    // Invalid attempt at using object as an array.
    classBuilder.addStaticMethod(
        "objectAsArray",
        ImmutableList.of("Ljava/lang/Object;"),
        "V",
        ".limit stack 10",
        ".limit locals 10",
        ".catch java/lang/NullPointerException from L0 to L1 using L2",
        "aload 0",
        "iconst_0",
        "iconst_1",
        "L0:",
        "iastore",
        "L1:",
        "return",
        "L2:",
        "pop",
        "getstatic java/lang/System/out Ljava/io/PrintStream;",
        "ldc \"NPE\"",
        "invokevirtual java/io/PrintStream/println(Ljava/lang/String;)V",
        "return");
    classBuilder.addMainMethod(
        ".limit stack 5",
        ".limit locals 5",
        "aconst_null",
        "invokestatic TestClass/objectAsArray(Ljava/lang/Object;)V",
        "return");

    Path riJar = temp.getRoot().toPath().resolve("ri-out.jar");
    jasminBuilder.writeJar(riJar, new DefaultDiagnosticsHandler());
    ProcessResult riResult = ToolHelper.runJava(riJar, "TestClass");
    Assert.assertEquals(riResult.toString(), 1, riResult.exitCode);
    Assert.assertTrue(riResult.stderr.contains("VerifyError"));

    Path d8Jar = temp.getRoot().toPath().resolve("d8-out.jar");
    try {
      D8.run(
          D8Command.builder().addProgramFiles(riJar).setOutput(d8Jar, OutputMode.DexIndexed)
              .build());
    } catch (RuntimeException e) {
      // Discard expected failure.
      // If we ever start post-verifying inputs on internal errors this will need to be updated.
      Assert.assertTrue(e.getCause() instanceof AssertionError);
      return;
    }
    Assert.fail("Expected D8 to fail compilation");
  }
}
