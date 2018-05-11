// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.code;

import static com.android.tools.r8.utils.DexInspectorMatchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.VmTestRunner;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.jasmin.JasminBuilder.ClassBuilder;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.ClassSubject;
import com.android.tools.r8.utils.DexInspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VmTestRunner.class)
public class ClassWithNativeMethodTest extends TestBase {

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
    cls.addNativeMethod("n1", ImmutableList.of("Ljava/lang/String;"), "V",
        "return");
    cls.addNativeMethod("n2", ImmutableList.of("Ljava/lang/String;"), "V",
        "return");
    cls.addNativeMethod("n3", ImmutableList.of("Ljava/lang/String;"), "V",
        "return");
    cls.addNativeMethod("n4", ImmutableList.of("Ljava/lang/String;"), "V",
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

    Path outputDirectory = temp.newFolder().toPath();
    jasminBuilder.writeClassFiles(outputDirectory);
    ProcessResult javaResult = ToolHelper.runJava(outputDirectory, mainClassName);
    // Native .o is not given. Expect to see JNI error.
    assertNotEquals(0, javaResult.exitCode);
    assertThat(javaResult.stderr, containsString("JNI"));

    AndroidApp processedApp = compileWithD8(jasminBuilder.build());

    DexInspector inspector = new DexInspector(processedApp);
    ClassSubject mainSubject = inspector.clazz(cls.name);
    MethodSubject nativeMethod =
        mainSubject.method("void", "n1", ImmutableList.of("java.lang.String"));
    assertThat(nativeMethod, isPresent());

    ProcessResult artResult = runOnArtRaw(processedApp, mainClassName);
    // ART can process main() even without native definitions.
    assertEquals(0, artResult.exitCode);
    assertThat(artResult.stdout, containsString("foo"));
  }


}
