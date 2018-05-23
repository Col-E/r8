// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jasmin;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.jasmin.JasminBuilder.ClassFileVersion;
import com.google.common.collect.ImmutableList;
import org.junit.Test;

public class Regress80124071 extends JasminTestBase {

  private JasminBuilder buildClass() {
    JasminBuilder builder = new JasminBuilder(ClassFileVersion.JDK_1_4);
    JasminBuilder.ClassBuilder clazz = builder.addClass("Test");

    clazz.addPrivateVirtualMethod("privateMethod", ImmutableList.of(), "V",
        ".limit stack 2",
        ".limit locals 1",
        "getstatic java/lang/System/out Ljava/io/PrintStream;",
        "ldc \"privateMethod\"",
        "invokevirtual java/io/PrintStream/println(Ljava/lang/String;)V",
        "return");

    clazz.addDefaultConstructor();

    clazz.addMainMethod(
        ".limit stack 3",
        ".limit locals 1",
        "new Test",
        "dup",
        "invokespecial Test/<init>()V",
        // Should have been invokespecial but JVM is OK with it so we need to transform
        // to invoke-direct to be able to run on Art.
        "invokevirtual Test/privateMethod()V",
        "new TestSub",
        "dup",
        "dup",
        "invokespecial TestSub/<init>()V",
        // Should have been invokespecial but JVM is OK with it and invokes the private method
        // on the Test class. Therefore, there is no virtual dispatch and we need to transform
        // to invoke-direct to be able to run on Art.
        "invokevirtual Test/privateMethod()V",
        "invokevirtual TestSub/privateMethod()V",
        "return");

    JasminBuilder.ClassBuilder subclazz = builder.addClass("TestSub", "Test");

    subclazz.addVirtualMethod("privateMethod", ImmutableList.of(), "V",
        ".limit stack 2",
        ".limit locals 1",
        "getstatic java/lang/System/out Ljava/io/PrintStream;",
        "ldc \"privateMethod2\"",
        "invokevirtual java/io/PrintStream/println(Ljava/lang/String;)V",
        "return");

    return builder;
  }

  @Test
  public void test() throws Exception {
    JasminBuilder builder = buildClass();
    String jvm = runOnArtDx(builder, "Test");
    String dx = runOnJava(builder, "Test");
    assertEquals(jvm, dx);
    String d8 = runOnArtD8(builder, "Test");
    assertEquals(jvm, d8);
  }
}
