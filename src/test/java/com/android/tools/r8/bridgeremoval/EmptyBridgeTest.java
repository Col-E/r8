// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.bridgeremoval;

import static com.android.tools.r8.utils.DexInspectorMatchers.isPresent;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.jasmin.JasminBuilder.ClassBuilder;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.ClassSubject;
import com.android.tools.r8.utils.DexInspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import org.junit.Test;

public class EmptyBridgeTest extends TestBase {

  @Test
  public void test() throws Exception {
    JasminBuilder jasminBuilder = new JasminBuilder();

    ClassBuilder abs = jasminBuilder.addClass("Abs");
    abs.setAccess("public abstract");
    abs.addMethod("public bridge abstract", "emptyBridge", ImmutableList.of(), "V");

    ClassBuilder cls = jasminBuilder.addClass("Main");
    cls.addVirtualMethod("bogus", ImmutableList.of(), "V",
        ".limit stack 3",
        ".limit locals 2",
        "getstatic java/lang/System/out Ljava/io/PrintStream;",
        "ldc \"foo\"",
        "invokevirtual java/io/PrintStream/print(Ljava/lang/String;)V",
        "return");
    cls.addMainMethod(
        ".limit stack 4",
        ".limit locals 2",
        "new " + cls.name,
        "dup",
        "invokespecial " + cls.name + "/<init>()V",
        "astore_0",
        "aload_0",
        "invokevirtual " + cls.name + "/bogus()V",
        "return");

    String absClassName = abs.name;
    String mainClassName = cls.name;
    String proguardConfig =
        "-allowaccessmodification" + System.lineSeparator()
        + "-keep class " + mainClassName + "{ *; }" + System.lineSeparator()
            + "-keep class " + absClassName + "{ *; }";
    AndroidApp processedApp = compileWithR8(jasminBuilder.build(), proguardConfig);

    DexInspector inspector = new DexInspector(processedApp);
    ClassSubject classSubject = inspector.clazz(absClassName);
    assertThat(classSubject, isPresent());
    MethodSubject methodSubject = classSubject.method("void", "emptyBridge", ImmutableList.of());
    assertThat(methodSubject, isPresent());
  }

}
