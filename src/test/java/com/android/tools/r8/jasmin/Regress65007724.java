// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jasmin;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class Regress65007724 extends JasminTestBase {
  @Test
  public void testThat16BitsIndexAreAllowed() throws Exception {
    JasminBuilder builder = new JasminBuilder();

    for (int i = 0; i < 35000; i++) {
      builder.addClass("C" + i);
    }

    JasminBuilder.ClassBuilder clazz = builder.addClass("Test");

    clazz.addStaticField("f", "LC34000;", null);

    clazz.addMainMethod(
        ".limit stack 2",
        ".limit locals 1",
        "getstatic java/lang/System/out Ljava/io/PrintStream;",
        "ldc \"Hello World!\"",
        "invokevirtual java/io/PrintStream/print(Ljava/lang/String;)V",
        "return");

    String expected = runOnJava(builder, clazz.name);
    String artResult = runOnArtD8(builder, clazz.name);
    assertEquals(expected, artResult);
  }
}
