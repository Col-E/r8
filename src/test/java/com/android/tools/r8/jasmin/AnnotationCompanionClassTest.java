// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jasmin;

import static org.junit.Assert.assertFalse;

import com.android.tools.r8.ir.desugar.InterfaceMethodRewriter;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import org.junit.Test;

public class AnnotationCompanionClassTest extends JasminTestBase {

  private JasminBuilder buildClass() {
    JasminBuilder builder = new JasminBuilder(JasminBuilder.ClassFileVersion.JDK_1_4);
    JasminBuilder.ClassBuilder clazz =
        builder.addInterface("MyAnnotation", "java/lang/annotation/Annotation");

    clazz.setAccess("public interface abstract annotation");

    clazz.addStaticMethod(
        "staticMethod", ImmutableList.of(), "V",
        ".limit stack 0",
        ".limit locals 0",
        "  return");
    return builder;
  }

  @Test
  public void test() throws Exception {
    JasminBuilder builder = buildClass();
    AndroidApp androidApp = compileWithD8(builder);

    CodeInspector codeInspector = new CodeInspector(androidApp);
    assertFalse(
        codeInspector
            .clazz("LMyAnnotation" + InterfaceMethodRewriter.COMPANION_CLASS_NAME_SUFFIX + ";")
            .isAnnotation());
  }
}
