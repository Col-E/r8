// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite.staticvalues;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.jasmin.JasminTestBase;
import com.android.tools.r8.utils.AndroidApp;
import com.google.common.collect.ImmutableList;
import org.junit.Test;

public class B67468748 extends JasminTestBase {

  @Test
  public void initializeStaticFieldInSuper() throws Exception {
    final String SUPER_NAME = "Super";
    final String CLASS_NAME = "Test";

    JasminBuilder builder = new JasminBuilder();

    // Simple super class with just a static field.
    JasminBuilder.ClassBuilder zuper = builder.addClass(SUPER_NAME);
    zuper.addStaticField("intField", "I", null);

    JasminBuilder.ClassBuilder clazz = builder.addClass(CLASS_NAME, SUPER_NAME);

    // The static field Test/intField is actually defined on the super class.
    clazz.addStaticMethod("<clinit>", ImmutableList.of(), "V",
        ".limit stack 1",
        ".limit locals 1",
        "iconst_1",
        "putstatic Test/intField I",
        "return");

    clazz.addMainMethod(
        ".limit stack 2",
        ".limit locals 1",
        "getstatic java/lang/System/out Ljava/io/PrintStream;",
        "getstatic Test/intField I",
        "invokevirtual java/io/PrintStream/print(I)V",
        "return");

    // Run in release mode to turn on initializer defaults rewriting.
    AndroidApp application = compileWithD8(builder, options -> options.debug = false);
    String result = runOnArt(application, CLASS_NAME);
    assertEquals("1", result);
  }

  @Test
  public void invalidCode() throws Exception {
    final String CLASS_NAME = "Test";

    JasminBuilder builder = new JasminBuilder();
    JasminBuilder.ClassBuilder clazz = builder.addClass(CLASS_NAME);

    // The static field Test/intField is not defined even though it is written in the
    // <clinit> code. This class cannot load, but we can still process it to dex, where Art also
    // cannot load the class.
    clazz.addStaticMethod("<clinit>", ImmutableList.of(), "V",
        ".limit stack 1",
        ".limit locals 1",
        "iconst_1",
        "putstatic Test/intField I",
        "return");

    clazz.addMainMethod(
        ".limit stack 2",
        ".limit locals 1",
        "getstatic java/lang/System/out Ljava/io/PrintStream;",
        "getstatic Test/intField I",
        "invokevirtual java/io/PrintStream/print(I)V",
        "return");

    // The code does not run on the Java VM, as there is a missing field.
    ProcessResult result = runOnJavaRaw(builder, CLASS_NAME);
    assertEquals(1, result.exitCode);
    assertTrue(result.stderr.contains("java.lang.NoSuchFieldError"));

    // Run in release mode to turn on initializer defaults rewriting.
    AndroidApp application = compileWithD8(builder, options -> options.debug = false);

    // The code does not run on Art, as there is a missing field.
    result = runOnArtRaw(application, CLASS_NAME);
    assertEquals(1, result.exitCode);
    assertTrue(result.stderr.contains("java.lang.NoSuchFieldError"));
  }

  // Test with dex input is in StaticValuesTest.
}
