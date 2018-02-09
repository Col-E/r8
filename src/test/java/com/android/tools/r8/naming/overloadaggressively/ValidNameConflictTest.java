// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.overloadaggressively;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.ToolHelper.DexVm.Kind;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.jasmin.JasminBuilder.ClassBuilder;
import com.android.tools.r8.jasmin.JasminTestBase;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.ClassSubject;
import com.android.tools.r8.utils.DexInspector.FieldSubject;
import com.android.tools.r8.utils.DexInspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ValidNameConflictTest extends JasminTestBase {
  private final String CLASS_NAME = "Example";
  private final String MSG = "You are seeing undefined behavior.";

  private final String REFLECTIONS =
      "-identifiernamestring public class java.lang.Class {\n"
          + "  public java.lang.reflect.Field getField(java.lang.String);\n"
          + "  public java.lang.reflect.Method getMethod(java.lang.String, java.lang.Class[]);"
          + "}";

  private final DexVm dexVm;

  public ValidNameConflictTest(DexVm dexVm) {
    this.dexVm = dexVm;
  }

  @Parameters(name = "vm: {0}")
  public static Collection<Object> data() {
    List<Object> testCases = new ArrayList<>();
    for (DexVm version : DexVm.values()) {
      if (version.getKind() == Kind.HOST) {
        testCases.add(version);
      }
    }
    return testCases;
  }

  private JasminBuilder buildFieldNameConflictClassFile() throws Exception {
    JasminBuilder builder = new JasminBuilder();
    ClassBuilder classBuilder = builder.addClass(CLASS_NAME);
    classBuilder.addStaticField("same", "Ljava/lang/String;", "\"" + MSG + "\"");
    classBuilder.addStaticField("same", "Ljava/lang/Object;", null);
    classBuilder.addMainMethod(
        ".limit stack 3",
        ".limit locals 1",
        "ldc Example",
        "ldc \"same\"",
        "invokevirtual java/lang/Class/getField(Ljava/lang/String;)Ljava/lang/reflect/Field;",
        "astore_0",
        "getstatic java/lang/System/out Ljava/io/PrintStream;",
        "aload_0",
        "aconst_null",
        "invokevirtual java/lang/reflect/Field/get(Ljava/lang/Object;)Ljava/lang/Object;",
        "invokevirtual java/io/PrintStream/print(Ljava/lang/Object;)V",
        "return");
    return builder;
  }

  @Test
  public void remainFieldNameConflictDueToKeepRules() throws Exception {
    Assume.assumeTrue(ToolHelper.artSupported());
    JasminBuilder builder = buildFieldNameConflictClassFile();
    ProcessResult javaOutput = runOnJavaNoVerifyRaw(builder, CLASS_NAME);
    assertEquals(0, javaOutput.exitCode);

    List<String> pgConfigs = ImmutableList.of(
        "-keep public class " + CLASS_NAME + " {\n"
            + "  public static void main(java.lang.String[]);\n"
            + "  static <fields>;"
            + "}\n"
            + "-printmapping\n",
        REFLECTIONS,
        "-dontshrink");
    AndroidApp app = compileWithR8(builder, pgConfigs, null);

    DexInspector dexInspector = new DexInspector(app);
    ClassSubject clazz = dexInspector.clazz(CLASS_NAME);
    assertTrue(clazz.isPresent());
    FieldSubject f1 = clazz.field("java.lang.String", "same");
    assertTrue(f1.isPresent());
    assertFalse(f1.isRenamed());
    FieldSubject f2 = clazz.field("java.lang.Object", "same");
    assertTrue(f2.isPresent());
    assertFalse(f2.isRenamed());
    assertEquals(f1.getField().field.name, f2.getField().field.name);

    ProcessResult artOutput = runOnArtRaw(app, CLASS_NAME, dexVm);
    assertEquals(0, artOutput.exitCode);
    // With reserved *same* names, it is not guaranteed to have same output.
    // assertEquals(javaOutput.stdout, artOutput.stdout);
  }


  @Test
  public void remainFieldNameConflictWithUseUniqueClassMemberNames() throws Exception {
    Assume.assumeTrue(ToolHelper.artSupported());
    JasminBuilder builder = buildFieldNameConflictClassFile();
    ProcessResult javaOutput = runOnJavaNoVerifyRaw(builder, CLASS_NAME);
    assertEquals(0, javaOutput.exitCode);

    List<String> pgConfigs = ImmutableList.of(
        keepMainProguardConfiguration(CLASS_NAME),
        REFLECTIONS,
        "-useuniqueclassmembernames",
        "-dontshrink");
    AndroidApp app = compileWithR8(builder, pgConfigs, null);

    DexInspector dexInspector = new DexInspector(app);
    ClassSubject clazz = dexInspector.clazz(CLASS_NAME);
    assertTrue(clazz.isPresent());
    FieldSubject f1 = clazz.field("java.lang.String", "same");
    assertTrue(f1.isPresent());
    assertTrue(f1.isRenamed());
    FieldSubject f2 = clazz.field("java.lang.Object", "same");
    assertTrue(f2.isPresent());
    assertTrue(f2.isRenamed());
    // TODO(b/73149686): -useuniqueclassmembernames for field minification is buggy.
    // assertEquals(f1.getField().field.name, f2.getField().field.name);

    ProcessResult artOutput = runOnArtRaw(app, CLASS_NAME, dexVm);
    assertEquals(0, artOutput.exitCode);
    // TODO(b/73149686): with reserved *same* names, it is not guaranteed to have same output.
    assertEquals(javaOutput.stdout, artOutput.stdout);
  }

  @Test
  public void resolveFieldNameConflictWithoutAnyOption() throws Exception {
    Assume.assumeTrue(ToolHelper.artSupported());
    JasminBuilder builder = buildFieldNameConflictClassFile();
    ProcessResult javaOutput = runOnJavaNoVerifyRaw(builder, CLASS_NAME);
    assertEquals(0, javaOutput.exitCode);

    List<String> pgConfigs = ImmutableList.of(
        keepMainProguardConfiguration(CLASS_NAME),
        REFLECTIONS,
        "-dontshrink");
    AndroidApp app = compileWithR8(builder, pgConfigs, null);

    DexInspector dexInspector = new DexInspector(app);
    ClassSubject clazz = dexInspector.clazz(CLASS_NAME);
    assertTrue(clazz.isPresent());
    FieldSubject f1 = clazz.field("java.lang.String", "same");
    assertTrue(f1.isPresent());
    assertTrue(f1.isRenamed());
    FieldSubject f2 = clazz.field("java.lang.Object", "same");
    assertTrue(f2.isPresent());
    assertTrue(f2.isRenamed());
    assertNotEquals(f1.getField().field.name, f2.getField().field.name);

    ProcessResult artOutput = runOnArtRaw(app, CLASS_NAME, dexVm);
    assertEquals(0, artOutput.exitCode);
    assertEquals(javaOutput.stdout, artOutput.stdout);
  }

  @Test
  public void resolveFieldNameConflictEvenWithOverloadAggressively() throws Exception {
    Assume.assumeTrue(ToolHelper.artSupported());
    JasminBuilder builder = buildFieldNameConflictClassFile();
    ProcessResult javaOutput = runOnJavaNoVerifyRaw(builder, CLASS_NAME);
    assertEquals(0, javaOutput.exitCode);

    List<String> pgConfigs = ImmutableList.of(
        keepMainProguardConfiguration(CLASS_NAME),
        REFLECTIONS,
        "-overloadaggressively",
        "-dontshrink");
    AndroidApp app = compileWithR8(builder, pgConfigs, null);

    DexInspector dexInspector = new DexInspector(app);
    ClassSubject clazz = dexInspector.clazz(CLASS_NAME);
    assertTrue(clazz.isPresent());
    FieldSubject f1 = clazz.field("java.lang.String", "same");
    assertTrue(f1.isPresent());
    assertTrue(f1.isRenamed());
    FieldSubject f2 = clazz.field("java.lang.Object", "same");
    assertTrue(f2.isPresent());
    assertTrue(f2.isRenamed());
    // TODO(b/72858955): R8 should resolve this field name conflict.
    assertEquals(f1.getField().field.name, f2.getField().field.name);

    ProcessResult artOutput = runOnArtRaw(app, CLASS_NAME, dexVm);
    assertEquals(0, artOutput.exitCode);
    // TODO(b/72858955): distinct names will make the output be same.
    // assertEquals(javaOutput.stdout, artOutput.stdout);
  }

  private JasminBuilder buildMethodNameConflictClassFile() throws Exception {
    JasminBuilder builder = new JasminBuilder();
    ClassBuilder classBuilder = builder.addClass(CLASS_NAME);
    classBuilder.addStaticMethod("same", ImmutableList.of(), "Ljava/lang/String;",
        "ldc \"" + MSG + "\"",
        "areturn");
    classBuilder.addStaticMethod("same", ImmutableList.of(), "Ljava/lang/Object;",
        "aconst_null",
        "areturn");
    classBuilder.addMainMethod(
        ".limit stack 3",
        ".limit locals 1",
        "ldc Example",
        "ldc \"same\"",
        "aconst_null",
        "checkcast [Ljava/lang/Class;",
        "invokevirtual java/lang/Class/getMethod"
            + "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;",
        "astore_0",
        "getstatic java/lang/System/out Ljava/io/PrintStream;",
        "aload_0",
        "aconst_null",
        "aconst_null",
        "checkcast [Ljava/lang/Object;",
        "invokevirtual java/lang/reflect/Method/invoke"
            + "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;",
        "invokevirtual java/io/PrintStream/print(Ljava/lang/Object;)V",
        "return");
    return builder;
  }

  @Test
  public void remainMethodNameConflictDueToKeepRules() throws Exception {
    Assume.assumeTrue(ToolHelper.artSupported());
    JasminBuilder builder = buildMethodNameConflictClassFile();
    ProcessResult javaOutput = runOnJavaNoVerifyRaw(builder, CLASS_NAME);
    assertEquals(0, javaOutput.exitCode);

    List<String> pgConfigs = ImmutableList.of(
        "-keep public class " + CLASS_NAME + " {\n"
            + "  public static void main(java.lang.String[]);\n"
            + "  static <methods>;"
            + "}\n"
            + "-printmapping\n",
        REFLECTIONS,
        "-useuniqueclassmembernames",
        "-dontshrink");
    AndroidApp app = compileWithR8(builder, pgConfigs, null);

    DexInspector dexInspector = new DexInspector(app);
    ClassSubject clazz = dexInspector.clazz(CLASS_NAME);
    assertTrue(clazz.isPresent());
    MethodSubject m1 = clazz.method("java.lang.String", "same", ImmutableList.of());
    assertTrue(m1.isPresent());
    assertFalse(m1.isRenamed());
    MethodSubject m2 = clazz.method("java.lang.Object", "same", ImmutableList.of());
    assertTrue(m2.isPresent());
    assertFalse(m2.isRenamed());
    assertEquals(m1.getMethod().method.name, m2.getMethod().method.name);

    ProcessResult artOutput = runOnArtRaw(app, CLASS_NAME, dexVm);
    assertEquals(0, artOutput.exitCode);
    // With name conflict, it is not guaranteed to get the same output.
    // assertEquals(javaOutput.stdout, artOutput.stdout);
  }

  @Test
  public void remainMethodNameConflictWithUseUniqueClassMemberNames() throws Exception {
    Assume.assumeTrue(ToolHelper.artSupported());
    JasminBuilder builder = buildMethodNameConflictClassFile();
    ProcessResult javaOutput = runOnJavaNoVerifyRaw(builder, CLASS_NAME);
    assertEquals(0, javaOutput.exitCode);

    List<String> pgConfigs = ImmutableList.of(
        keepMainProguardConfiguration(CLASS_NAME),
        REFLECTIONS,
        "-useuniqueclassmembernames",
        "-dontshrink");
    AndroidApp app = compileWithR8(builder, pgConfigs, null);

    DexInspector dexInspector = new DexInspector(app);
    ClassSubject clazz = dexInspector.clazz(CLASS_NAME);
    assertTrue(clazz.isPresent());
    MethodSubject m1 = clazz.method("java.lang.String", "same", ImmutableList.of());
    assertTrue(m1.isPresent());
    assertTrue(m1.isRenamed());
    MethodSubject m2 = clazz.method("java.lang.Object", "same", ImmutableList.of());
    assertTrue(m2.isPresent());
    assertTrue(m2.isRenamed());
    assertEquals(m1.getMethod().method.name, m2.getMethod().method.name);

    ProcessResult artOutput = runOnArtRaw(app, CLASS_NAME, dexVm);
    assertEquals(0, artOutput.exitCode);
    // With name conflict, it is not guaranteed to get the same output.
    // assertEquals(javaOutput.stdout, artOutput.stdout);
  }

  @Test
  public void resolveMethodNameConflictWithoutAnyOption() throws Exception {
    Assume.assumeTrue(ToolHelper.artSupported());
    JasminBuilder builder = buildMethodNameConflictClassFile();
    ProcessResult javaOutput = runOnJavaNoVerifyRaw(builder, CLASS_NAME);
    assertEquals(0, javaOutput.exitCode);

    List<String> pgConfigs = ImmutableList.of(
        keepMainProguardConfiguration(CLASS_NAME),
        REFLECTIONS,
        "-dontshrink");
    AndroidApp app = compileWithR8(builder, pgConfigs, null);

    DexInspector dexInspector = new DexInspector(app);
    ClassSubject clazz = dexInspector.clazz(CLASS_NAME);
    assertTrue(clazz.isPresent());
    MethodSubject m1 = clazz.method("java.lang.String", "same", ImmutableList.of());
    assertTrue(m1.isPresent());
    assertTrue(m1.isRenamed());
    MethodSubject m2 = clazz.method("java.lang.Object", "same", ImmutableList.of());
    assertTrue(m2.isPresent());
    assertTrue(m2.isRenamed());
    // TODO(b/73149686): R8 should be able to fix this conflict w/o -overloadaggressively.
    // assertNotEquals(m1.getMethod().method.name, m2.getMethod().method.name);

    ProcessResult artOutput = runOnArtRaw(app, CLASS_NAME, dexVm);
    assertEquals(0, artOutput.exitCode);
    // TODO(b/73149686): distinct names will output the same results.
    // assertEquals(javaOutput.stdout, artOutput.stdout);
  }

  @Test
  public void resolveMethodNameConflictEvenWithOverloadAggressively() throws Exception {
    Assume.assumeTrue(ToolHelper.artSupported());
    JasminBuilder builder = buildMethodNameConflictClassFile();
    ProcessResult javaOutput = runOnJavaNoVerifyRaw(builder, CLASS_NAME);
    assertEquals(0, javaOutput.exitCode);

    List<String> pgConfigs = ImmutableList.of(
        keepMainProguardConfiguration(CLASS_NAME),
        REFLECTIONS,
        "-overloadaggressively",
        "-dontshrink");
    AndroidApp app = compileWithR8(builder, pgConfigs, null);

    DexInspector dexInspector = new DexInspector(app);
    ClassSubject clazz = dexInspector.clazz(CLASS_NAME);
    assertTrue(clazz.isPresent());
    MethodSubject m1 = clazz.method("java.lang.String", "same", ImmutableList.of());
    assertTrue(m1.isPresent());
    assertTrue(m1.isRenamed());
    MethodSubject m2 = clazz.method("java.lang.Object", "same", ImmutableList.of());
    assertTrue(m2.isPresent());
    assertTrue(m2.isRenamed());
    // TODO(b/73149686): R8 should be able to fix this conflict even w/ -overloadaggressively.
    assertEquals(m1.getMethod().method.name, m2.getMethod().method.name);

    ProcessResult artOutput = runOnArtRaw(app, CLASS_NAME, dexVm);
    assertEquals(0, artOutput.exitCode);
    // TODO(b/73149686): distinct names will output the same results.
    // assertEquals(javaOutput.stdout, artOutput.stdout);
  }

}
