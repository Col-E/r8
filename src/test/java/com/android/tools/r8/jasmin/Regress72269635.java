// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jasmin;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.jasmin.JasminBuilder.ClassFileVersion;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.AndroidApp;
import com.google.common.collect.ImmutableList;
import org.junit.Test;

public class Regress72269635 extends JasminTestBase {

  @Test
  public void testDeadBlocksRemoved() throws Exception {
    JasminBuilder builder = new JasminBuilder(ClassFileVersion.JDK_1_4);
    JasminBuilder.ClassBuilder clazz = builder.addClass("Test");

    clazz.addStaticMethod("test", ImmutableList.of("IIII"), "J",
        ".limit stack 3",
        ".limit locals 5",
        "L0:",
        "  iconst_2",
        "  ifge L0",
        "L48:",
        "  iload 3",
        "  ifle L48",
        "  goto L209",
        "  aconst_null",
        "  athrow",
        "L209:",
        "  aconst_null",
        "  iconst_2",
        "  newarray float",
        "  if_acmpne L250",
        "L240:",
        "  iload 3",
        "  pop",
        "L250:",
        "  iload 3",
        "  ifgt L240",
        "  iload 3",
        "  istore 0",
        "  goto L357",
        "L318:",
        "  iinc 0 1",
        "L351:",
        "  iinc 0 1",
        "  goto L351",
        "L357:",
        "  iinc 0 1",
        "L415:",
        "  iinc 0 1",
        "  goto L415"
    );

    // Just compiling this code used to throw exceptions because not all dead blocks
    // were removed.
    AndroidApp originalApplication = builder.build();
    ToolHelper.runD8(originalApplication);
  }
}
