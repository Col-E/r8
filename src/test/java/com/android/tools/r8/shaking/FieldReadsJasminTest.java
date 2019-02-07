// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.jasmin.JasminBuilder.ClassBuilder;
import com.android.tools.r8.jasmin.JasminTestBase;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.Iterator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class FieldReadsJasminTest extends JasminTestBase {
  private static final String CLS = "Empty";
  private static final String MAIN = "Main";
  private final Backend backend;

  @Parameterized.Parameters(name = "Backend: {0}")
  public static Object[] data() {
    return Backend.values();
  }

  public FieldReadsJasminTest(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void testInstanceGet_nonNullReceiver() throws Exception {
    JasminBuilder builder = new JasminBuilder();

    ClassBuilder empty = builder.addClass(CLS);
    empty.addField("protected", "aField", "I", null);

    ClassBuilder main = builder.addClass(MAIN);
    main.addMainMethod(
        ".limit stack 2",
        ".limit locals 1",
        "  new Empty",
        "  dup",
        "  invokespecial Empty/<init>()V",
        "  getfield Empty/aField I",
        "  return");

    ensureNoFields(builder, empty);
  }

  @Test
  public void testStaticGet_noSideEffect() throws Exception {
    JasminBuilder builder = new JasminBuilder();

    ClassBuilder empty = builder.addClass(CLS);
    empty.addStaticField("sField", "I");

    ClassBuilder main = builder.addClass(MAIN);
    main.addMainMethod(
        ".limit stack 2",
        ".limit locals 1",
        "  getstatic Empty/sField I",
        "  return");

    ensureNoFields(builder, empty);
  }

  private void ensureNoFields(JasminBuilder app, ClassBuilder clazz) throws Exception {
    testForR8(backend)
        .addProgramClassFileData(app.buildClasses())
        .addKeepRules("-keep class * { <methods>; }")
        .compile()
        .inspect(inspector -> {
          ClassSubject classSubject = inspector.clazz(clazz.name);
          assertThat(classSubject, isPresent());
          classSubject.forAllFields(foundFieldSubject -> {
            fail("Expect not to see any fields.");
          });
        });
  }

  @Test
  public void testInstanceGet_nullableReceiver() throws Exception {
    JasminBuilder builder = new JasminBuilder();

    ClassBuilder empty = builder.addClass(CLS);
    empty.addDefaultConstructor();
    empty.addField("protected", "aField", "I", null);
    MethodSignature foo = empty.addStaticMethod("foo", ImmutableList.of("L" + CLS + ";"), "V",
        ".limit stack 2",
        ".limit locals 1",
        "  aload 0",
        "  getfield Empty/aField I",
        "  return");

    ClassBuilder main = builder.addClass(MAIN);
    main.addMainMethod(
        ".limit stack 2",
        ".limit locals 1",
        "  aconst_null",
        "  invokestatic Empty/foo(L" + CLS + ";)V",
        "  return");

    ensureFieldExists(builder, empty, foo, "aField");
  }

  @Test
  public void testStaticGet_nonTrivialClinit() throws Exception {
    JasminBuilder builder = new JasminBuilder();

    ClassBuilder empty = builder.addClass(CLS);
    empty.addDefaultConstructor();
    empty.addStaticField("sField", "I");
    empty.addClassInitializer(
        ".limit stack 3",
        ".limit locals 1",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  ldc \"hello\"",
        "  invokevirtual java/io/PrintStream/print(Ljava/lang/String;)V",
        "  return");

    ClassBuilder main = builder.addClass(MAIN);
    MethodSignature mainMethod = main.addMainMethod(
        ".limit stack 2",
        ".limit locals 1",
        "  getstatic Empty/sField I",
        "  return");

    ensureFieldExists(builder, main, mainMethod, "sField");
  }

  @Test
  public void testStaticGet_allocation() throws Exception {
    JasminBuilder builder = new JasminBuilder();

    ClassBuilder empty = builder.addClass(CLS);
    empty.addDefaultConstructor();
    empty.addStaticField("sField", "Ljava/lang/String;", "\"8\"");

    ClassBuilder main = builder.addClass(MAIN);
    MethodSignature mainMethod = main.addMainMethod(
        ".limit stack 2",
        ".limit locals 1",
        "  getstatic Empty/sField Ljava/lang/String;",
        "  return");

    ensureFieldExists(builder, main, mainMethod, "sField");
  }

  private void ensureFieldExists(
      JasminBuilder app, ClassBuilder clazz, MethodSignature method, String fieldName)
      throws Exception {
    testForR8(backend)
        .addProgramClassFileData(app.buildClasses())
        .addKeepRules("-keep class * { <methods>; }")
        .compile()
        .inspect(inspector -> {
          ClassSubject classSubject = inspector.clazz(clazz.name);
          assertThat(classSubject, isPresent());
          MethodSubject methodSubject = classSubject.uniqueMethodWithName(method.name);
          assertThat(methodSubject, isPresent());
          Iterator<InstructionSubject> it =
              methodSubject.iterateInstructions(InstructionSubject::isFieldAccess);
          assertTrue(it.hasNext());
          assertEquals(
              inspector.clazz(CLS).uniqueFieldWithName(fieldName).getFinalName(),
              it.next().getField().name.toString());
        });
  }

}
