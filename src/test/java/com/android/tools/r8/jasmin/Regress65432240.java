// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jasmin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.code.IfNez;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.AndroidApp;
import com.google.common.collect.ImmutableList;
import org.junit.Test;

public class Regress65432240 extends JasminTestBase {

  @Test
  public void testConstantNotIntoEntryBlock() throws Exception {
    JasminBuilder builder = new JasminBuilder();
    JasminBuilder.ClassBuilder clazz = builder.addClass("Test");

    MethodSignature signature = clazz.addStaticMethod("test1", ImmutableList.of("I"), "I",
        ".limit stack 3",
        ".limit locals 2",
        "  iload 0",
        "  ifne L2",
        "L1:",
        "  iconst_0",
        "  ireturn",
        "L2:",
        "  iload 0",
        "  iload 0",
        "  iconst_1",
        "  isub",
        "  invokestatic Test/test1(I)I",
        "  iadd",
        "  ireturn");

    clazz.addMainMethod(
        ".limit stack 2",
        ".limit locals 1",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  iconst_0",
        "  invokestatic Test/test1(I)I",
        "  invokestatic java/lang/Integer/toString(I)Ljava/lang/String;",
        "  invokevirtual java/io/PrintStream/print(Ljava/lang/String;)V",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  iconst_0",
        "  invokestatic Test/test1(I)I",
        "  invokestatic java/lang/Integer/toString(I)Ljava/lang/String;",
        "  invokevirtual java/io/PrintStream/print(Ljava/lang/String;)V",
        "  return");

    String expected = runOnJava(builder, clazz.name);

    AndroidApp originalApplication = builder.build();
    AndroidApp processedApplication = ToolHelper.runR8(originalApplication);

    DexEncodedMethod method = getMethod(processedApplication, clazz.name, signature);
    DexCode code = method.getCode().asDexCode();
    assertTrue(code.instructions[0] instanceof IfNez);

    String artResult = runOnArtR8(builder, clazz.name);
    assertEquals(expected, artResult);
  }
}
