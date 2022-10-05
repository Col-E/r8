// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.jasmin.JasminBuilder.ClassBuilder;
import com.android.tools.r8.jasmin.JasminTestBase;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.Iterator;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class FieldReadsJasminTest extends JasminTestBase {
  private static final String CLS = "Empty";
  private static final String MAIN = "Main";

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testInstanceGet_nonNullReceiver() throws Exception {
    JasminBuilder builder = new JasminBuilder();

    ClassBuilder main = builder.addClass(MAIN);
    main.addField("protected", "aField", "I", null);
    main.addMainMethod(
        ".limit stack 2",
        ".limit locals 1",
        "  new Main",
        "  dup",
        "  invokespecial Main/<init>()V",
        "  getfield Main/aField I",
        "  return");

    ensureNoFieldsRead(builder, main);
  }

  @Test
  public void testStaticGet_noSideEffect() throws Exception {
    JasminBuilder builder = new JasminBuilder();

    ClassBuilder main = builder.addClass(MAIN);
    main.addStaticField("sField", "I");
    main.addMainMethod(
        ".limit stack 2",
        ".limit locals 1",
        "  getstatic Main/sField I",
        "  return");

    ensureNoFieldsRead(builder, main);
  }

  @Test
  public void testStaticGet_allocation() throws Exception {
    JasminBuilder builder = new JasminBuilder();

    ClassBuilder main = builder.addClass(MAIN);
    main.addDefaultConstructor();
    main.addStaticField("sField", "Ljava/lang/String;", "\"8\"");
    main.addMainMethod(
        ".limit stack 2",
        ".limit locals 1",
        "  getstatic Main/sField Ljava/lang/String;",
        "  return");

    ensureNoFieldsRead(builder, main);
  }

  private void ensureNoFieldsRead(JasminBuilder app, ClassBuilder clazz) throws Exception {
    List<byte[]> classes = app.buildClasses();

    if (parameters.isDexRuntime()) {
      testForD8()
          .addProgramClassFileData(classes)
          .setMinApi(parameters.getApiLevel())
          .compile()
          .inspect(inspector -> ensureNoFieldsRead(inspector, clazz.name, false));
    }

    testForR8(parameters.getBackend())
        .addProgramClassFileData(classes)
        .addKeepRules("-keep class * { <methods>; }")
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(inspector -> ensureNoFieldsRead(inspector, clazz.name, true));
  }

  private void ensureNoFieldsRead(CodeInspector inspector, String name, boolean isR8) {
    ClassSubject classSubject = inspector.clazz(name);
    assertThat(classSubject, isPresent());
    if (isR8) {
      classSubject.forAllFields(foundFieldSubject -> {
        fail("Expect not to see any fields.");
      });
    }
    MethodSubject mainMethod = classSubject.mainMethod();
    assertThat(mainMethod, isPresent());
    Iterator<InstructionSubject> it =
        mainMethod.iterateInstructions(InstructionSubject::isFieldAccess);
    assertFalse(it.hasNext());
  }

  @Test
  public void testStaticGet_nonTrivialClinit_yetSameHolder() throws Exception {
    JasminBuilder builder = new JasminBuilder();

    ClassBuilder main = builder.addClass(MAIN);
    // static int sField = System.currentTimeMillis() >=0 ? 42 : 0;
    main.addStaticField("sField", "I", null);
    main.addClassInitializer(
        ".limit stack 4",
        ".limit locals 0",
        "  invokestatic java/lang/System/currentTimeMillis()J",
        "  lconst_0",
        "  lcmp",
        "  iflt l",
        "  bipush 42",
        "  goto p",
        "l:",
        "  iconst_0",
        "p:",
        "  putstatic Main/sField I",
        "  return");

    main.addMainMethod(
        ".limit stack 2", ".limit locals 1", "  getstatic Main/sField I", "  return");

    testForR8(parameters.getBackend())
        .addProgramClassFileData(builder.buildClasses())
        .addKeepRules("-keep class * { <methods>; }")
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(
            inspector ->
                assertThat(
                    inspector.clazz(main.name).uniqueFieldWithOriginalName("sField"), isAbsent()));
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

    inspect(
        builder,
        inspector ->
            ensureFieldExistsAndReadOnlyOnce(
                inspector, empty.name, foo.name, empty, "aField", false),
        inspector -> {
          ClassSubject emptyClassSubject = inspector.clazz(CLS);
          assertThat(emptyClassSubject, isPresent());
          assertTrue(emptyClassSubject.allFields().isEmpty());

          MethodSubject fooMethodSubject = emptyClassSubject.uniqueMethodWithOriginalName("foo");
          assertThat(fooMethodSubject, isPresent());
          assertTrue(
              fooMethodSubject
                  .streamInstructions()
                  .filter(InstructionSubject::isInvoke)
                  .anyMatch(
                      invoke ->
                          invoke.getMethod().toSourceString().contains("requireNonNull")
                              || invoke.getMethod().toSourceString().contains("getClass")));
        });
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

    inspect(
        builder,
        inspector ->
            ensureFieldExistsAndReadOnlyOnce(
                inspector, main.name, mainMethod.name, empty, "sField", false),
        inspector -> {
          ClassSubject emptyClassSubject = inspector.clazz(empty.name);
          assertThat(emptyClassSubject, isPresent());
          assertEquals(1, emptyClassSubject.allStaticFields().size());

          FieldSubject clinitFieldSubject = emptyClassSubject.allStaticFields().get(0);
          assertEquals("$r8$clinit", clinitFieldSubject.getOriginalName());

          ClassSubject mainClassSubject = inspector.clazz(main.name);
          assertThat(mainClassSubject, isPresent());
          assertThat(mainClassSubject.mainMethod(), isPresent());
          assertTrue(
              mainClassSubject
                  .mainMethod()
                  .streamInstructions()
                  .filter(InstructionSubject::isStaticGet)
                  .anyMatch(
                      instruction ->
                          instruction
                              .getField()
                              .equals(clinitFieldSubject.getField().getReference())));
        });
  }

  @Test
  public void b124039115() throws Exception {
    JasminBuilder builder = new JasminBuilder();

    ClassBuilder empty = builder.addClass(CLS);
    empty.addDefaultConstructor();
    empty.addClassInitializer(
        ".limit stack 2",
        ".limit locals 0",
        "  getstatic Main/sField I",
        "  iconst_1",
        "  iadd",
        "  putstatic Main/sField I",
        "  return");

    ClassBuilder main = builder.addClass(MAIN);
    main.addDefaultConstructor();
    main.addStaticField("sField", "I", null);
    main.addClassInitializer(
        ".limit stack 2",
        ".limit locals 0",
        "  bipush 1",
        "  putstatic Main/sField I",
        "  return");
    MethodSignature mainMethod =
        main.addMainMethod(
            ".limit stack 4",
            ".limit locals 2",
            "  getstatic Main/sField I",
            "  new Empty",
            "  dup",
            "  invokespecial Empty/<init>()V",
            "  getstatic Main/sField I",
            "  bipush 2",
            "  if_icmpeq r",
            "  aconst_null",
            "  athrow",
            "r:",
            "  return");

    ensureFieldExistsAndReadOnlyOnce(builder, main, mainMethod, main, "sField");
  }

  private void inspect(
      JasminBuilder app,
      ThrowingConsumer<CodeInspector, RuntimeException> d8Inspector,
      ThrowingConsumer<CodeInspector, RuntimeException> r8Inspector)
      throws Exception {
    List<byte[]> classes = app.buildClasses();

    if (parameters.isDexRuntime()) {
      testForD8()
          .addProgramClassFileData(classes)
          .setMinApi(parameters.getApiLevel())
          .compile()
          .inspect(d8Inspector);
    }

    testForR8(parameters.getBackend())
        .addProgramClassFileData(classes)
        .addKeepRules("-keep class * { <methods>; }")
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(r8Inspector);
  }

  private void ensureFieldExistsAndReadOnlyOnce(
      JasminBuilder app,
      ClassBuilder clazz,
      MethodSignature method,
      ClassBuilder fieldHolder,
      String fieldName)
      throws Exception {
    inspect(
        app,
        inspector ->
            ensureFieldExistsAndReadOnlyOnce(
                inspector, clazz.name, method.name, fieldHolder, fieldName, false),
        inspector ->
            ensureFieldExistsAndReadOnlyOnce(
                inspector, clazz.name, method.name, fieldHolder, fieldName, true));
  }

  private void ensureFieldExistsAndReadOnlyOnce(
      CodeInspector inspector,
      String className,
      String methodName,
      ClassBuilder fieldHolder,
      String fieldName,
      boolean isR8) {
    FieldSubject fld = inspector.clazz(fieldHolder.name).uniqueFieldWithOriginalName(fieldName);
    if (isR8) {
      assertThat(fld, isPresentAndRenamed());
    } else {
      assertThat(fld, isPresent());
    }

    ClassSubject classSubject = inspector.clazz(className);
    assertThat(classSubject, isPresent());
    MethodSubject methodSubject = classSubject.uniqueMethodWithOriginalName(methodName);
    assertThat(methodSubject, isPresent());
    Iterator<InstructionSubject> it =
        methodSubject.iterateInstructions(InstructionSubject::isFieldAccess);
    assertTrue(it.hasNext());
    assertEquals(fld.getFinalName(), it.next().getField().name.toString());
    assertFalse(it.hasNext());
  }
}
