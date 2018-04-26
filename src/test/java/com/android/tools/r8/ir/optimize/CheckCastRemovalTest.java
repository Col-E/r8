// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.code.AgetObject;
import com.android.tools.r8.code.AputObject;
import com.android.tools.r8.code.CheckCast;
import com.android.tools.r8.code.Const4;
import com.android.tools.r8.code.ConstString;
import com.android.tools.r8.code.IgetObject;
import com.android.tools.r8.code.InvokeDirectRange;
import com.android.tools.r8.code.InvokeVirtualRange;
import com.android.tools.r8.code.NewArray;
import com.android.tools.r8.code.NewInstance;
import com.android.tools.r8.code.ReturnVoid;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.jasmin.JasminBuilder.ClassBuilder;
import com.android.tools.r8.jasmin.JasminTestBase;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.AndroidApp;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;

public class CheckCastRemovalTest extends JasminTestBase {
  private final String CLASS_NAME = "Example";

  @Test
  public void exactMatch() throws Exception {
    JasminBuilder builder = new JasminBuilder();
    ClassBuilder classBuilder = builder.addClass(CLASS_NAME);
    classBuilder.addDefaultConstructor();
    MethodSignature main = classBuilder.addMainMethod(
        ".limit stack 3",
        ".limit locals 1",
        "new Example",
        "dup",
        "invokespecial Example/<init>()V",
        "checkcast Example", // Gone
        "return");

    List<String> pgConfigs = ImmutableList.of(
        "-keep class " + CLASS_NAME + " { *; }",
        "-dontshrink");
    AndroidApp app = compileWithR8(builder, pgConfigs, null);

    DexEncodedMethod method = getMethod(app, CLASS_NAME, main);
    assertNotNull(method);

    checkInstructions(
        method.getCode().asDexCode(),
        ImmutableList.of(NewInstance.class, InvokeDirectRange.class, ReturnVoid.class));

    checkRuntime(builder, app, CLASS_NAME);
  }

  @Test
  public void upCasts() throws Exception {
    JasminBuilder builder = new JasminBuilder();
    // A < B < C
    ClassBuilder c = builder.addClass("C");
    c.addDefaultConstructor();
    ClassBuilder b = builder.addClass("B", "C");
    b.addDefaultConstructor();
    ClassBuilder a = builder.addClass("A", "B");
    a.addDefaultConstructor();
    ClassBuilder classBuilder = builder.addClass(CLASS_NAME);
    MethodSignature main = classBuilder.addMainMethod(
        ".limit stack 3",
        ".limit locals 1",
        "new A",
        "dup",
        "invokespecial A/<init>()V",
        "checkcast B", // Gone
        "checkcast C", // Gone
        "return");

    List<String> pgConfigs = ImmutableList.of(
        "-keep class " + CLASS_NAME + " { *; }",
        "-keep class A { *; }",
        "-keep class B { *; }",
        "-keep class C { *; }",
        "-dontshrink");
    AndroidApp app = compileWithR8(builder, pgConfigs, null);

    DexEncodedMethod method = getMethod(app, CLASS_NAME, main);
    assertNotNull(method);

    checkInstructions(
        method.getCode().asDexCode(),
        ImmutableList.of(NewInstance.class, InvokeDirectRange.class, ReturnVoid.class));

    checkRuntime(builder, app, CLASS_NAME);
  }

  @Test
  public void downCasts() throws Exception {
    JasminBuilder builder = new JasminBuilder();
    // C < B < A
    ClassBuilder a = builder.addClass("A");
    a.addDefaultConstructor();
    ClassBuilder b = builder.addClass("B", "A");
    b.addDefaultConstructor();
    ClassBuilder c = builder.addClass("C", "B");
    c.addDefaultConstructor();
    ClassBuilder classBuilder = builder.addClass(CLASS_NAME);
    MethodSignature main = classBuilder.addMainMethod(
        ".limit stack 3",
        ".limit locals 1",
        "new A",
        "dup",
        "invokespecial A/<init>()V",
        "checkcast B", // Gone
        "checkcast C", // Should be kept
        "return");

    List<String> pgConfigs = ImmutableList.of(
        "-keep class " + CLASS_NAME + " { *; }",
        "-keep class A { *; }",
        "-keep class B { *; }",
        "-keep class C { *; }",
        "-dontoptimize",
        "-dontshrink");
    AndroidApp app = compileWithR8(builder, pgConfigs, null);

    DexEncodedMethod method = getMethod(app, CLASS_NAME, main);
    assertNotNull(method);

    DexCode code = method.getCode().asDexCode();
    checkInstructions(
        code,
        ImmutableList.of(
            NewInstance.class, InvokeDirectRange.class, CheckCast.class, ReturnVoid.class));
    CheckCast cast = (CheckCast) code.instructions[2];
    assertEquals("C", cast.getType().toString());

    checkRuntimeException(builder, app, CLASS_NAME, "ClassCastException");
  }

  @Test
  public void bothUpAndDowncast() throws Exception {
    JasminBuilder builder = new JasminBuilder();
    ClassBuilder classBuilder = builder.addClass(CLASS_NAME);
    MethodSignature main = classBuilder.addMainMethod(
        ".limit stack 4",
        ".limit locals 1",
        "iconst_1",
        "anewarray java/lang/String", // args parameter
        "dup",
        "iconst_0",
        "ldc \"a string\"",
        "aastore",
        "checkcast [Ljava/lang/Object;",  // This upcast can be removed.
        "iconst_0",
        "aaload",
        "checkcast java/lang/String",  // Then, this downcast can be removed, too.
        "invokevirtual java/lang/String/length()I",
        "return");
    // That is, both checkcasts should be removed together or kept together.

    List<String> pgConfigs = ImmutableList.of(
        "-keep class " + CLASS_NAME + " { *; }",
        "-dontshrink");
    AndroidApp app = compileWithR8(builder, pgConfigs, null);

    DexEncodedMethod method = getMethod(app, CLASS_NAME, main);
    assertNotNull(method);

    DexCode code = method.getCode().asDexCode();
    checkInstructions(
        code,
        ImmutableList.of(
            Const4.class,
            NewArray.class,
            ConstString.class,
            Const4.class,
            AputObject.class,
            AgetObject.class,
            InvokeVirtualRange.class,
            ReturnVoid.class));

    checkRuntime(builder, app, CLASS_NAME);
  }

  @Test
  public void nullCast() throws Exception {
    JasminBuilder builder = new JasminBuilder();
    ClassBuilder classBuilder = builder.addClass(CLASS_NAME);
    classBuilder.addField("public", "fld", "Ljava/lang/String;", null);
    MethodSignature main = classBuilder.addMainMethod(
        ".limit stack 3",
        ".limit locals 1",
        "aconst_null",
        "checkcast Example", // Should be kept
        "getfield Example.fld Ljava/lang/String;",
        "return");

    List<String> pgConfigs = ImmutableList.of(
        "-keep class " + CLASS_NAME + " { *; }",
        "-dontshrink");
    AndroidApp app = compileWithR8(builder, pgConfigs, null);

    DexEncodedMethod method = getMethod(app, CLASS_NAME, main);
    assertNotNull(method);

    checkInstructions(method.getCode().asDexCode(), ImmutableList.of(
        Const4.class,
        CheckCast.class,
        IgetObject.class,
        ReturnVoid.class));

    checkRuntimeException(builder, app, CLASS_NAME, "NullPointerException");
  }

  private void checkRuntime(JasminBuilder builder, AndroidApp app, String className)
      throws Exception {
    String normalOutput = runOnJava(builder, className);
    String dexOptimizedOutput = runOnArt(app, className);
    assertEquals(normalOutput, dexOptimizedOutput);
  }

  private void checkRuntimeException(
      JasminBuilder builder, AndroidApp app, String className, String exceptionName)
      throws Exception {
    ProcessResult javaOutput = runOnJavaRaw(builder, className);
    assertEquals(1, javaOutput.exitCode);
    assertTrue(javaOutput.stderr.contains(exceptionName));

    ProcessResult artOutput = runOnArtRaw(app, className);
    assertEquals(1, artOutput.exitCode);
    assertTrue(artOutput.stderr.contains(exceptionName));
  }
}
