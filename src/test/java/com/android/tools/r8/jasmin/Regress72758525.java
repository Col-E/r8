// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jasmin;

import com.android.tools.r8.jasmin.JasminBuilder.ClassFileVersion;
import org.junit.Test;

public class Regress72758525 extends JasminTestBase {

  private JasminBuilder buildClass() {
    JasminBuilder builder = new JasminBuilder(ClassFileVersion.JDK_1_4);
    JasminBuilder.ClassBuilder clazz = builder.addClass("Test");

    clazz.addDefaultConstructor();

    clazz.addMainMethod(
        ".limit stack 25",
        ".limit locals 1",
        "aload 0",
        "dup",
        "lconst_0",
        "dconst_1",
        "fconst_0",
        "lconst_1",
        "iconst_5",
        "fconst_1",
        "dconst_1",
        "new Test",
        "dup",
        "invokespecial Test/<init>()V",
        "lconst_0",
        "new java/lang/Object",
        "dup",
        "invokespecial java/lang/Object/<init>()V",
        "iconst_m1",
        "dup2",
        "dup2_x2",
        "L0:",
        "ineg",
        "new java/lang/Object",
        "dup",
        "invokespecial java/lang/Object/<init>()V",
        "dup2_x2",
        "pop2",
        "pop",
        "aload 0",
        "ifnull L0",
        "i2f",
        "invokestatic java/lang/Float/isNaN(F)Z",
        "return");
    return builder;
  }

  @Test
  public void test() throws Exception {
    JasminBuilder builder = buildClass();
    runOnArtD8(builder, "Test");
  }
}
