// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.checkcast;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.jasmin.JasminBuilder.ClassBuilder;
import com.android.tools.r8.jasmin.JasminTestBase;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CheckCastRemovalTest extends JasminTestBase {

  private static final String CLASS_NAME = "Example";

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void exactMatch() throws Exception {
    JasminBuilder builder = new JasminBuilder();
    ClassBuilder classBuilder = builder.addClass(CLASS_NAME);
    classBuilder.addDefaultConstructor();
    classBuilder.addMainMethod(
        ".limit stack 3",
        ".limit locals 1",
        "new Example",
        "dup",
        "invokespecial Example/<init>()V",
        "checkcast Example", // Gone
        "return");

    testForR8(parameters.getBackend())
        .addProgramClassFileData(builder.buildClasses())
        .addKeepMainRule(CLASS_NAME)
        .setMinApi(parameters)
        .compile()
        .inspect(inspector -> checkCheckCasts(inspector.clazz(CLASS_NAME).mainMethod()))
        .run(parameters.getRuntime(), CLASS_NAME)
        .assertSuccessWithEmptyOutput();
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
    classBuilder.addMainMethod(
        ".limit stack 3",
        ".limit locals 1",
        "new A",
        "dup",
        "invokespecial A/<init>()V",
        "checkcast B", // Gone
        "checkcast C", // Gone
        "return");

    testForR8(parameters.getBackend())
        .addProgramClassFileData(builder.buildClasses())
        .addKeepClassAndMembersRules(CLASS_NAME, "A", "B", "C")
        .setMinApi(parameters)
        .compile()
        .inspect(inspector -> checkCheckCasts(inspector.clazz(CLASS_NAME).mainMethod()))
        .run(parameters.getRuntime(), CLASS_NAME)
        .assertSuccessWithEmptyOutput();
  }

  @Test
  public void bothUpAndDowncast() throws Exception {
    JasminBuilder builder = new JasminBuilder();
    ClassBuilder classBuilder = builder.addClass(CLASS_NAME);
    classBuilder.addMainMethod(
        ".limit stack 4",
        ".limit locals 1",
        "iconst_1",
        "anewarray java/lang/String", // args parameter
        "dup",
        "iconst_0",
        "ldc \"a string\"",
        "aastore",
        "checkcast [Ljava/lang/Object;", // This upcast can be removed.
        "iconst_0",
        "aaload",
        "checkcast java/lang/String", // Then, this downcast can be removed, too.
        "invokevirtual java/lang/String/length()I",
        "return");

    // That is, both checkcasts should be removed together or kept together.
    testForR8(parameters.getBackend())
        .addProgramClassFileData(builder.buildClasses())
        .addKeepMainRule(CLASS_NAME)
        .setMinApi(parameters)
        .compile()
        .inspect(inspector -> checkCheckCasts(inspector.clazz(CLASS_NAME).mainMethod()))
        .run(parameters.getRuntime(), CLASS_NAME)
        .assertSuccessWithEmptyOutput();
  }

  @Test
  public void nullCast() throws Exception {
    JasminBuilder builder = new JasminBuilder();
    ClassBuilder classBuilder = builder.addClass(CLASS_NAME);
    classBuilder.addField("public", "fld", "Ljava/lang/String;", null);
    classBuilder.addMainMethod(
        ".limit stack 3",
        ".limit locals 1",
        "aconst_null",
        "checkcast Example", // Should be kept
        "getfield Example.fld Ljava/lang/String;",
        "return");

    testForR8(parameters.getBackend())
        .addProgramClassFileData(builder.buildClasses())
        .addKeepClassAndMembersRules(CLASS_NAME)
        .setMinApi(parameters)
        .compile()
        .inspect(inspector -> checkCheckCasts(inspector.clazz(CLASS_NAME).mainMethod()))
        .run(parameters.getRuntime(), CLASS_NAME)
        .assertFailureWithErrorThatThrows(NullPointerException.class);
  }

  private void checkCheckCasts(MethodSubject method) {
    assertTrue(method.streamInstructions().noneMatch(InstructionSubject::isCheckCast));
  }
}
