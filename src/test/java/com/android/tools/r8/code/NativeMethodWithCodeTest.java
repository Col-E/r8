// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.code;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.VmTestRunner;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.jasmin.JasminBuilder.ClassBuilder;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VmTestRunner.class)
public class NativeMethodWithCodeTest extends TestBase {

  // Test that D8 removes code from native methods (to match the behavior of dx).
  // Note that the JVM rejects a class if it has a native method with a code attribute,
  // but D8 has to handle that and cannot simply throw an error even though the JVM does.

  @Test
  public void test() throws Exception {
    JasminBuilder jasminBuilder = new JasminBuilder();

    ClassBuilder cls = jasminBuilder.addClass("Main");
    cls.addDefaultConstructor();
    cls.addVirtualMethod("foo", ImmutableList.of("Ljava/lang/String;"), "V",
        ".limit stack 3",
        ".limit locals 2",
        "getstatic java/lang/System/out Ljava/io/PrintStream;",
        "aload_1",
        "invokevirtual java/io/PrintStream/print(Ljava/lang/String;)V",
        "return");
    cls.addNativeMethodWithCode("n1", ImmutableList.of("Ljava/lang/String;"), "V",
        "return");
    cls.addNativeMethodWithCode("n2", ImmutableList.of("Ljava/lang/String;"), "V",
        "return");
    cls.addNativeMethodWithCode("n3", ImmutableList.of("Ljava/lang/String;"), "V",
        "return");
    cls.addNativeMethodWithCode("n4", ImmutableList.of("Ljava/lang/String;"), "V",
        "return");
    cls.addMainMethod(
        ".limit stack 4",
        ".limit locals 2",
        "new " + cls.name,
        "dup",
        "invokespecial " + cls.name + "/<init>()V",
        "astore_0",
        "aload_0",
        "ldc \"foo\"",
        "invokevirtual " + cls.name + "/foo(Ljava/lang/String;)V",
        "return");

    String mainClassName = cls.name;

    Path inputJar = temp.getRoot().toPath().resolve("input.jar");
    jasminBuilder.writeJar(inputJar, null);
    AndroidApp inputApp = AndroidApp.builder().addProgramFiles(inputJar).build();
    // TODO(mathiasr): Consider making frontend read in code on native methods.
    // If we do that, this assertNull should change to assertNotNull.
    assertNull(getNativeMethod(mainClassName, inputApp).getMethod().getCode());

    // JVM throws ClassFormatError because the input contains code on a native method. (JVM8§4.7.3)
    ProcessResult javaResult = ToolHelper.runJava(inputJar, mainClassName);
    assertNotEquals(0, javaResult.exitCode);
    assertThat(javaResult.stderr, containsString("ClassFormatError"));

    // DX strips code from native methods.
    Path dxOutputDir = temp.newFolder().toPath();
    Path dxOutputFile = dxOutputDir.resolve("classes.dex");
    ToolHelper.runDexer(inputJar.toString(), dxOutputDir.toString());
    AndroidApp dxApp = AndroidApp.builder().addProgramFiles(dxOutputFile).build();
    assertNull(getNativeMethod(mainClassName, dxApp).getMethod().getCode());

    // D8 should also strip code from native methods.
    AndroidApp processedApp = compileWithD8(inputApp);
    assertThat(getNativeMethod(mainClassName, processedApp), isPresent());
    // TODO(mathiasr): Consider making D8 maintain code on native methods.
    // If we do that, this assertNull should change to assertNotNull.
    assertNull(getNativeMethod(mainClassName, processedApp).getMethod().getCode());

    ProcessResult artResult = runOnArtRaw(processedApp, mainClassName);
    assertEquals(0, artResult.exitCode);
    assertThat(artResult.stdout, containsString("foo"));
  }

  private MethodSubject getNativeMethod(String mainClassName, AndroidApp processedApp)
      throws IOException, ExecutionException {
    CodeInspector inspector = new CodeInspector(processedApp);
    ClassSubject mainSubject = inspector.clazz(mainClassName);
    return mainSubject.method("void", "n1", ImmutableList.of("java.lang.String"));
  }
}
