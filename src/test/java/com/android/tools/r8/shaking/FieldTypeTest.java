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
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VmTestRunner.class)
public class FieldTypeTest extends TestBase {

  private ClassBuilder addImplementor(
      JasminBuilder jasminBuilder, String name, String superName, String... interfaces) {
    ClassBuilder impl = jasminBuilder.addClass(name, superName, interfaces);
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
    return impl;
  }

  @Test
  public void test_brokenTypeHierarchy() throws Exception {
    JasminBuilder jasminBuilder = new JasminBuilder();
    // interface Itf1
    ClassBuilder itf1 = jasminBuilder.addInterface("Itf1");
    MethodSignature foo1 = itf1.addAbstractMethod("foo", ImmutableList.of(), "V");
    // class Impl1 /* implements Itf1 */
    ClassBuilder impl1 = addImplementor(jasminBuilder, "Impl1", "java/lang/Object");

    // Another interface and implementer with a correct relation.
    ClassBuilder itf2 = jasminBuilder.addInterface("Itf2");
    MethodSignature foo2 = itf2.addAbstractMethod("foo", ImmutableList.of(), "V");
    ClassBuilder impl2 = addImplementor(jasminBuilder, "Impl2", "java/lang/Object", itf2.name);

    ClassBuilder client = jasminBuilder.addClass("Client");
    client.setAccess("final");
    client.addDefaultConstructor();
    FieldSignature a = client.addStaticFinalField("a", "Ljava/lang/Object;", null);
    FieldSignature obj1 = client.addField("private static", "obj1", itf1.getDescriptor(), null);
    FieldSignature obj2 = client.addStaticFinalField("obj2", itf2.getDescriptor(), null);
    client.addClassInitializer(
        ".limit locals 1",
        ".limit stack 2",
        "aconst_null",
        "putstatic " + client.name + "/" + a.name  + " " + "Ljava/lang/Object;",
        "new " + impl1.name,
        "dup",
        "invokespecial " + impl1.name + "/<init>()V",
        // Unused, i.e., not read, field, yet still remained in the output.
        "putstatic " + client.name + "/" + obj1.name + " " + itf1.getDescriptor(),
        "new " + impl2.name,
        "dup",
        "invokespecial " + impl2.name + "/<init>()V",
        "putstatic " + client.name + "/" + obj2.name + " " + itf2.getDescriptor(),
        "return"
    );

    ClassBuilder mainClass = jasminBuilder.addClass("Main");
    mainClass.addMainMethod(
        ".limit locals 2",
        ".limit stack 2",
        "getstatic java/lang/System/out Ljava/io/PrintStream;",
        /*
        "getstatic " + client.name + "/" + obj1.name + " " + itf1.getDescriptor(),
        "astore_0",
        "aload_0",
        // java.lang.IncompatibleClassChangeError:
        //     Class Impl does not implement the requested interface Itf
        "invokeinterface " + itf.name + "/" + foo.name + "()V 1",
        "aload_0",
        */
        "getstatic " + client.name + "/" + obj2.name + " " + itf2.getDescriptor(),
        "invokevirtual java/io/PrintStream/print(Ljava/lang/Object;)V",
        "return"
    );

    final String mainClassName = mainClass.name;
    String proguardConfig =
        keepMainProguardConfiguration(mainClass.name, false, false)
            // AGP default is to not turn optimizations on, which disables MemberValuePropagation,
            // resulting in the problematic putstatic being remained.
            + "-dontoptimize\n";

    // Run input program on java.
    Path outputDirectory = temp.newFolder().toPath();
    jasminBuilder.writeClassFiles(outputDirectory);
    ProcessResult javaResult = ToolHelper.runJava(outputDirectory, mainClassName);
    assertEquals(0, javaResult.exitCode);
    assertThat(javaResult.stdout, containsString(impl2.name));

    AndroidApp processedApp = compileWithR8(jasminBuilder.build(), proguardConfig,
        // Disable inlining to avoid the (short) tested method from being inlined and then removed.
        internalOptions -> internalOptions.enableInlining = false);

    // Run processed (output) program on ART
    ProcessResult artResult = runOnArtRaw(processedApp, mainClassName);
    assertEquals(0, artResult.exitCode);
    assertThat(artResult.stdout, containsString(impl2.name));
    assertEquals(-1, artResult.stderr.indexOf("DoFieldPut"));

    DexInspector inspector = new DexInspector(processedApp);
    ClassSubject itf1Subject = inspector.clazz(itf1.name);
    assertThat(itf1Subject, isPresent());
  }

}
