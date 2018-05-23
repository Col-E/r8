// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.bridgeremoval;

import static com.android.tools.r8.utils.DexInspectorMatchers.isPresent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.bridgeremoval.bridgestoremove.Main;
import com.android.tools.r8.bridgeremoval.bridgestoremove.Outer;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.jasmin.JasminBuilder.ClassBuilder;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.ClassSubject;
import com.android.tools.r8.utils.DexInspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class RemoveVisibilityBridgeMethodsTest extends TestBase {

  private void run(boolean obfuscate) throws Exception {
    List<Class> classes = ImmutableList.of(
        Outer.class,
        Main.class);
    String proguardConfig = keepMainProguardConfiguration(Main.class, true, obfuscate);
    DexInspector inspector = new DexInspector(compileWithR8(classes, proguardConfig));

    List<Method> removedMethods = ImmutableList.of(
        Outer.SubClass.class.getMethod("method"),
        Outer.StaticSubClass.class.getMethod("method"));

    removedMethods.forEach(method -> assertFalse(inspector.method(method).isPresent()));
  }

  @Test
  public void testWithObfuscation() throws Exception {
    run(true);
  }

  @Test
  public void testWithoutObfuscation() throws Exception {
    run(false);
  }

  @Test
  public void regressionTest_b76383728_WithObfuscation() throws Exception {
    runRegressionTest_b76383728(true);
  }

  @Test
  public void regressionTest_b76383728_WithoutObfuscation() throws Exception {
    runRegressionTest_b76383728(false);
  }

  /**
   * Regression test for b76383728 to make sure we correctly identify and remove real visibility
   * forward bridge methods synthesized by javac.
   */
  private void runRegressionTest_b76383728(boolean obfuscate) throws Exception {
    JasminBuilder jasminBuilder = new JasminBuilder();

    ClassBuilder superClass = jasminBuilder.addClass("SuperClass");
    superClass.addDefaultConstructor();
    superClass.addVirtualMethod("method", Collections.emptyList(), "Ljava/lang/String;",
        ".limit stack 1",
        "ldc \"Hello World\"",
        "areturn");

    // Generate a subclass with a method with same
    ClassBuilder subclass = jasminBuilder.addClass("SubClass", superClass.name);
    subclass.addBridgeMethod("getMethod", Collections.emptyList(), "Ljava/lang/String;",
        ".limit stack 1",
        "aload_0",
        "invokespecial " + superClass.name + "/method()Ljava/lang/String;",
        "areturn");

    ClassBuilder mainClass = jasminBuilder.addClass("Main");
    mainClass.addMainMethod(
        ".limit stack 3",
        ".limit locals 2",
        "getstatic java/lang/System/out Ljava/io/PrintStream;",
        "new " + subclass.name,
        "dup",
        "invokespecial " + subclass.name + "/<init>()V",
        "invokevirtual " + subclass.name + "/getMethod()Ljava/lang/String;",
        "invokevirtual java/io/PrintStream/print(Ljava/lang/String;)V",
        "return"
    );

    final String mainClassName = mainClass.name;

    String proguardConfig = keepMainProguardConfiguration(mainClass.name, true, obfuscate);

    // Run input program on java.
    Path outputDirectory = temp.newFolder().toPath();
    jasminBuilder.writeClassFiles(outputDirectory);
    ProcessResult javaResult = ToolHelper.runJava(outputDirectory, mainClassName);
    assertEquals(0, javaResult.exitCode);

    AndroidApp optimizedApp = compileWithR8(jasminBuilder.build(), proguardConfig,
        // Disable inlining to avoid the (short) tested method from being inlined and then removed.
        internalOptions -> internalOptions.enableInlining = false);

    // Run optimized (output) program on ART
    String artResult = runOnArt(optimizedApp, mainClassName);
    assertEquals(javaResult.stdout, artResult);

    DexInspector inspector = new DexInspector(optimizedApp);

    ClassSubject classSubject = inspector.clazz(superClass.name);
    assertThat(classSubject, isPresent());
    MethodSubject methodSubject = classSubject
        .method("java.lang.String", "method", Collections.emptyList());
    assertThat(methodSubject, isPresent());

    classSubject = inspector.clazz(subclass.name);
    assertThat(classSubject, isPresent());
    methodSubject = classSubject.method("java.lang.String", "getMethod", Collections.emptyList());
    assertThat(methodSubject, isPresent());
  }
}
