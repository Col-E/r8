// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static com.android.tools.r8.utils.DexInspectorMatchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.VmTestRunner;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.jasmin.JasminBuilder.ClassBuilder;
import com.android.tools.r8.naming.MemberNaming.FieldSignature;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.ClassSubject;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VmTestRunner.class)
public class FieldTypeTest extends TestBase {

  @Ignore("b/78788577")
  @Test
  public void test_brokenTypeHierarchy() throws Exception {
    JasminBuilder jasminBuilder = new JasminBuilder();
    // interface Itf
    ClassBuilder itf = jasminBuilder.addInterface("Itf");
    MethodSignature foo = itf.addAbstractMethod("foo", ImmutableList.of(), "V");
    // class Impl /* implements Itf */
    ClassBuilder impl = jasminBuilder.addClass("Impl");
    impl.addDefaultConstructor();
    impl.addVirtualMethod("foo", ImmutableList.of(), "V",
        ".limit locals 2",
        ".limit stack 2",
        "getstatic java/lang/System/out Ljava/io/PrintStream;",
        "ldc \"" + "foo" + "\"",
        "invokevirtual java/io/PrintStream/print(Ljava/lang/String;)V",
        "return");
    impl.addVirtualMethod("toString", ImmutableList.of(), "Ljava/lang/String;",
        ".limit locals 1",
        ".limit stack 2",
        "ldc \"" + impl.name + "\"",
        "areturn");
    ClassBuilder client = jasminBuilder.addClass("Client");
    FieldSignature obj = client.addStaticFinalField("obj", itf.getDescriptor(), null);
    client.addClassInitializer(
        ".limit locals 1",
        ".limit stack 2",
        "new " + impl.name,
        "dup",
        "invokespecial " + impl.name + "/<init>()V",
        "putstatic " + client.name + "/" + obj.name + " " + itf.getDescriptor(),
        "return"
    );

    ClassBuilder mainClass = jasminBuilder.addClass("Main");
    mainClass.addMainMethod(
        ".limit locals 2",
        ".limit stack 2",
        "getstatic java/lang/System/out Ljava/io/PrintStream;",
        "getstatic " + client.name + "/" + obj.name + " " + itf.getDescriptor(),
        /*
        "astore_0",
        "aload_0",
        // java.lang.IncompatibleClassChangeError:
        //     Class Impl does not implement the requested interface Itf
        "invokeinterface " + itf.name + "/" + foo.name + "()V 1",
        "aload_0",
        */
        "invokevirtual java/io/PrintStream/print(Ljava/lang/Object;)V",
        "return"
    );

    final String mainClassName = mainClass.name;
    String proguardConfig = keepMainProguardConfiguration(mainClass.name, false, false);

    // Run input program on java.
    Path outputDirectory = temp.newFolder().toPath();
    jasminBuilder.writeClassFiles(outputDirectory);
    ProcessResult javaResult = ToolHelper.runJava(outputDirectory, mainClassName);
    assertEquals(0, javaResult.exitCode);
    assertThat(javaResult.stdout, containsString(impl.name));

    AndroidApp processedApp = compileWithR8(jasminBuilder.build(), proguardConfig,
        // Disable inlining to avoid the (short) tested method from being inlined and then removed.
        internalOptions -> internalOptions.enableInlining = false);

    // Run processed (output) program on ART
    ProcessResult artResult = runOnArtRaw(processedApp, mainClassName);
    assertEquals(0, artResult.exitCode);
    assertThat(artResult.stdout, containsString(impl.name));
    assertEquals(-1, artResult.stderr.indexOf("DoFieldPut"));

    DexInspector inspector = new DexInspector(processedApp);
    ClassSubject itfSubject = inspector.clazz(itf.name);
    assertThat(itfSubject, isPresent());
  }

}
