// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.android.tools.r8.code.CheckCast;
import com.android.tools.r8.code.InvokeDirect;
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
        "-dontoptimize",
        "-dontshrink");
    AndroidApp app = compileWithR8(builder, pgConfigs, null);

    DexEncodedMethod method = getMethod(app, CLASS_NAME, main);
    assertNotNull(method);

    checkInstructions(method.getCode().asDexCode(), ImmutableList.of(
        NewInstance.class,
        InvokeDirect.class,
        ReturnVoid.class));
  }

  @Test
  public void upCasts() throws Exception {
    JasminBuilder builder = new JasminBuilder();
    // A < B < C
    builder.addClass("C");
    builder.addClass("B", "C");
    builder.addClass("A", "B");
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
        "-dontoptimize",
        "-dontshrink");
    AndroidApp app = compileWithR8(builder, pgConfigs, null);

    DexEncodedMethod method = getMethod(app, CLASS_NAME, main);
    assertNotNull(method);

    checkInstructions(method.getCode().asDexCode(), ImmutableList.of(
        NewInstance.class,
        InvokeDirect.class,
        ReturnVoid.class));
  }

  @Test
  public void downCasts() throws Exception {
    JasminBuilder builder = new JasminBuilder();
    // C < B < A
    builder.addClass("A");
    builder.addClass("B", "A");
    builder.addClass("C", "B");
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
    checkInstructions(code, ImmutableList.of(
        NewInstance.class,
        InvokeDirect.class,
        CheckCast.class,
        ReturnVoid.class));
    CheckCast cast = (CheckCast) code.instructions[2];
    assertEquals("C", cast.getType().toString());
  }
}
