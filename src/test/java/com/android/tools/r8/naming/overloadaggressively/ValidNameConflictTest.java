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
import com.google.common.collect.Iterables;
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
  private final String SUPER_CLASS = "Super";
  private final String ANOTHER_CLASS = "Test";
  private final String MSG = "Expected to be seen at the end.";

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

  private Iterable<String> buildCodeForVisitingDeclaredMembers(
      Iterable<String> prologue, Iterable<String> argumentLoadingAndCall) {
    return Iterables.concat(
        prologue,
        ImmutableList.of(
        "  astore_0",  // Member[]
        "  aload_0",
        "  arraylength",
        "  istore_1",  // Member[].length
        "  iconst_0",
        "  istore_2",  // counter
        "loop:",
        "  iload_2",
        "  iload_1",
        "  if_icmpge end",
        "  aload_0",
        "  iload_2",
        "  aaload",  // Member[counter]
        "  astore_3",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  aload_3"),
        argumentLoadingAndCall,
        ImmutableList.of(
        "  invokevirtual java/io/PrintStream/println(Ljava/lang/Object;)V",
        "  iinc 2 1",  // counter++
        "  goto loop",
        "end:",
        "  return"));
  }

  private JasminBuilder buildFieldNameConflictClassFile() {
    JasminBuilder builder = new JasminBuilder();
    ClassBuilder classBuilder = builder.addClass(CLASS_NAME);
    classBuilder.addStaticField("same", "Ljava/lang/Object;", null);
    classBuilder.addStaticField("same", "Ljava/lang/String;", "\"" + MSG + "\"");
    classBuilder.addMainMethod(
        buildCodeForVisitingDeclaredMembers(
            ImmutableList.of(
                ".limit stack 3",
                ".limit locals 4",
                "  ldc " + CLASS_NAME,
                "  invokevirtual java/lang/Class/getDeclaredFields()[Ljava/lang/reflect/Field;"),
            ImmutableList.of(
                "  aconst_null",
                "  invokevirtual java/lang/reflect/Field/get(Ljava/lang/Object;)Ljava/lang/Object;")));
    return builder;
  }

  @Test
  public void remainFieldNameConflict_keepRules() throws Exception {
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
    assertEquals(f1.getFinalName(), f2.getFinalName());

    ProcessResult artOutput = runOnArtRaw(app, CLASS_NAME, dexVm);
    assertEquals(0, artOutput.exitCode);
    assertEquals(javaOutput.stdout, artOutput.stdout);
  }

  @Test
  public void remainFieldNameConflict_useuniqueclassmembernames() throws Exception {
    Assume.assumeTrue(ToolHelper.artSupported());
    JasminBuilder builder = buildFieldNameConflictClassFile();
    ProcessResult javaOutput = runOnJavaNoVerifyRaw(builder, CLASS_NAME);
    assertEquals(0, javaOutput.exitCode);

    List<String> pgConfigs = ImmutableList.of(
        keepMainProguardConfiguration(CLASS_NAME),
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
    assertEquals(f1.getFinalName(), f2.getFinalName());

    ProcessResult artOutput = runOnArtRaw(app, CLASS_NAME, dexVm);
    assertEquals(0, artOutput.exitCode);
    assertEquals(javaOutput.stdout, artOutput.stdout);
  }

  @Test
  public void remainFieldNameConflict_useuniqueclassmembernames_overloadaggressively()
      throws Exception {
    Assume.assumeTrue(ToolHelper.artSupported());
    JasminBuilder builder = buildFieldNameConflictClassFile();
    ProcessResult javaOutput = runOnJavaNoVerifyRaw(builder, CLASS_NAME);
    assertEquals(0, javaOutput.exitCode);

    List<String> pgConfigs = ImmutableList.of(
        keepMainProguardConfiguration(CLASS_NAME),
        "-useuniqueclassmembernames",
        "-overloadaggressively",  // no-op
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
    assertEquals(f1.getFinalName(), f2.getFinalName());

    ProcessResult artOutput = runOnArtRaw(app, CLASS_NAME, dexVm);
    assertEquals(0, artOutput.exitCode);
    assertEquals(javaOutput.stdout, artOutput.stdout);
  }

  @Test
  public void resolveFieldNameConflict_no_options() throws Exception {
    Assume.assumeTrue(ToolHelper.artSupported());
    JasminBuilder builder = buildFieldNameConflictClassFile();
    ProcessResult javaOutput = runOnJavaNoVerifyRaw(builder, CLASS_NAME);
    assertEquals(0, javaOutput.exitCode);

    List<String> pgConfigs = ImmutableList.of(
        keepMainProguardConfiguration(CLASS_NAME),
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
    assertNotEquals(f1.getFinalName(), f2.getFinalName());

    ProcessResult artOutput = runOnArtRaw(app, CLASS_NAME, dexVm);
    assertEquals(0, artOutput.exitCode);
    assertEquals(javaOutput.stdout, artOutput.stdout);
  }

  @Test
  public void remainFieldNameConflict_overloadaggressively() throws Exception {
    Assume.assumeTrue(ToolHelper.artSupported());
    JasminBuilder builder = buildFieldNameConflictClassFile();
    ProcessResult javaOutput = runOnJavaNoVerifyRaw(builder, CLASS_NAME);
    assertEquals(0, javaOutput.exitCode);

    List<String> pgConfigs = ImmutableList.of(
        keepMainProguardConfiguration(CLASS_NAME),
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
    assertEquals(f1.getFinalName(), f2.getFinalName());

    ProcessResult artOutput = runOnArtRaw(app, CLASS_NAME, dexVm);
    assertEquals(0, artOutput.exitCode);
    assertEquals(javaOutput.stdout, artOutput.stdout);
  }

  private JasminBuilder buildMethodNameConflictClassFile() {
    JasminBuilder builder = new JasminBuilder();
    ClassBuilder classBuilder = builder.addClass(ANOTHER_CLASS);
    classBuilder.addStaticMethod("same", ImmutableList.of(), "Ljava/lang/Object;",
        "aconst_null",
        "areturn");
    classBuilder.addStaticMethod("same", ImmutableList.of(), "Ljava/lang/String;",
        "ldc \"" + MSG + "\"",
        "areturn");
    classBuilder = builder.addClass(CLASS_NAME);
    classBuilder.addMainMethod(
        buildCodeForVisitingDeclaredMembers(
            ImmutableList.of(
                ".limit stack 3",
                ".limit locals 4",
                "  ldc " + ANOTHER_CLASS,
                "  invokevirtual java/lang/Class/getDeclaredMethods()[Ljava/lang/reflect/Method;"),
            ImmutableList.of(
                "  aconst_null",
                "  aconst_null",
                "  checkcast [Ljava/lang/Object;",
                "  invokevirtual java/lang/reflect/Method/invoke"
                    + "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;")));
    return builder;
  }

  @Test
  public void remainMethodNameConflict_keepRules() throws Exception {
    Assume.assumeTrue(ToolHelper.artSupported());
    JasminBuilder builder = buildMethodNameConflictClassFile();
    ProcessResult javaOutput = runOnJavaNoVerifyRaw(builder, CLASS_NAME);
    assertEquals(0, javaOutput.exitCode);

    List<String> pgConfigs = ImmutableList.of(
        "-keep class " + ANOTHER_CLASS + " {\n"
            + "  static <methods>;"
            + "}\n",
        keepMainProguardConfiguration(CLASS_NAME),
        "-useuniqueclassmembernames",
        "-dontshrink");
    AndroidApp app = compileWithR8(builder, pgConfigs, null);

    DexInspector dexInspector = new DexInspector(app);
    ClassSubject clazz = dexInspector.clazz(ANOTHER_CLASS);
    assertTrue(clazz.isPresent());
    MethodSubject m1 = clazz.method("java.lang.String", "same", ImmutableList.of());
    assertTrue(m1.isPresent());
    assertFalse(m1.isRenamed());
    MethodSubject m2 = clazz.method("java.lang.Object", "same", ImmutableList.of());
    assertTrue(m2.isPresent());
    assertFalse(m2.isRenamed());
    assertEquals(m1.getFinalName(), m2.getFinalName());

    ProcessResult artOutput = runOnArtRaw(app, CLASS_NAME, dexVm);
    assertEquals(0, artOutput.exitCode);
    assertEquals(javaOutput.stdout, artOutput.stdout);
  }

  @Test
  public void remainMethodNameConflict_useuniqueclassmembernames() throws Exception {
    Assume.assumeTrue(ToolHelper.artSupported());
    JasminBuilder builder = buildMethodNameConflictClassFile();
    ProcessResult javaOutput = runOnJavaNoVerifyRaw(builder, CLASS_NAME);
    assertEquals(0, javaOutput.exitCode);

    List<String> pgConfigs = ImmutableList.of(
        keepMainProguardConfiguration(CLASS_NAME),
        "-useuniqueclassmembernames",
        "-dontshrink");
    AndroidApp app = compileWithR8(builder, pgConfigs, null);

    DexInspector dexInspector = new DexInspector(app);
    ClassSubject clazz = dexInspector.clazz(ANOTHER_CLASS);
    assertTrue(clazz.isPresent());
    MethodSubject m1 = clazz.method("java.lang.String", "same", ImmutableList.of());
    assertTrue(m1.isPresent());
    assertTrue(m1.isRenamed());
    MethodSubject m2 = clazz.method("java.lang.Object", "same", ImmutableList.of());
    assertTrue(m2.isPresent());
    assertTrue(m2.isRenamed());
    assertEquals(m1.getFinalName(), m2.getFinalName());

    ProcessResult artOutput = runOnArtRaw(app, CLASS_NAME, dexVm);
    assertEquals(0, artOutput.exitCode);
    assertEquals(javaOutput.stdout, artOutput.stdout);
  }

  @Test
  public void remainMethodNameConflict_useuniqueclassmembernames_overloadaggressively()
      throws Exception {
    Assume.assumeTrue(ToolHelper.artSupported());
    JasminBuilder builder = buildMethodNameConflictClassFile();
    ProcessResult javaOutput = runOnJavaNoVerifyRaw(builder, CLASS_NAME);
    assertEquals(0, javaOutput.exitCode);

    List<String> pgConfigs = ImmutableList.of(
        keepMainProguardConfiguration(CLASS_NAME),
        "-useuniqueclassmembernames",
        "-overloadaggressively",  // no-op
        "-dontshrink");
    AndroidApp app = compileWithR8(builder, pgConfigs, null);

    DexInspector dexInspector = new DexInspector(app);
    ClassSubject clazz = dexInspector.clazz(ANOTHER_CLASS);
    assertTrue(clazz.isPresent());
    MethodSubject m1 = clazz.method("java.lang.String", "same", ImmutableList.of());
    assertTrue(m1.isPresent());
    assertTrue(m1.isRenamed());
    MethodSubject m2 = clazz.method("java.lang.Object", "same", ImmutableList.of());
    assertTrue(m2.isPresent());
    assertTrue(m2.isRenamed());
    assertEquals(m1.getFinalName(), m2.getFinalName());

    ProcessResult artOutput = runOnArtRaw(app, CLASS_NAME, dexVm);
    assertEquals(0, artOutput.exitCode);
    assertEquals(javaOutput.stdout, artOutput.stdout);
  }

  @Test
  public void resolveMethodNameConflict_no_options() throws Exception {
    Assume.assumeTrue(ToolHelper.artSupported());
    JasminBuilder builder = buildMethodNameConflictClassFile();
    ProcessResult javaOutput = runOnJavaNoVerifyRaw(builder, CLASS_NAME);
    assertEquals(0, javaOutput.exitCode);

    List<String> pgConfigs = ImmutableList.of(
        keepMainProguardConfiguration(CLASS_NAME),
        "-dontshrink");
    AndroidApp app = compileWithR8(builder, pgConfigs, null);

    DexInspector dexInspector = new DexInspector(app);
    ClassSubject clazz = dexInspector.clazz(ANOTHER_CLASS);
    assertTrue(clazz.isPresent());
    MethodSubject m1 = clazz.method("java.lang.String", "same", ImmutableList.of());
    assertTrue(m1.isPresent());
    assertTrue(m1.isRenamed());
    MethodSubject m2 = clazz.method("java.lang.Object", "same", ImmutableList.of());
    assertTrue(m2.isPresent());
    assertTrue(m2.isRenamed());
    assertNotEquals(m1.getFinalName(), m2.getFinalName());

    ProcessResult artOutput = runOnArtRaw(app, CLASS_NAME, dexVm);
    assertEquals(0, artOutput.exitCode);
    assertEquals(javaOutput.stdout, artOutput.stdout);
  }

  @Test
  public void remainMethodNameConflict_overloadaggressively() throws Exception {
    Assume.assumeTrue(ToolHelper.artSupported());
    JasminBuilder builder = buildMethodNameConflictClassFile();
    ProcessResult javaOutput = runOnJavaNoVerifyRaw(builder, CLASS_NAME);
    assertEquals(0, javaOutput.exitCode);

    List<String> pgConfigs = ImmutableList.of(
        keepMainProguardConfiguration(CLASS_NAME),
        "-overloadaggressively",
        "-dontshrink");
    AndroidApp app = compileWithR8(builder, pgConfigs, null);

    DexInspector dexInspector = new DexInspector(app);
    ClassSubject clazz = dexInspector.clazz(ANOTHER_CLASS);
    assertTrue(clazz.isPresent());
    MethodSubject m1 = clazz.method("java.lang.String", "same", ImmutableList.of());
    assertTrue(m1.isPresent());
    assertTrue(m1.isRenamed());
    MethodSubject m2 = clazz.method("java.lang.Object", "same", ImmutableList.of());
    assertTrue(m2.isPresent());
    assertTrue(m2.isRenamed());
    assertEquals(m1.getFinalName(), m2.getFinalName());

    ProcessResult artOutput = runOnArtRaw(app, CLASS_NAME, dexVm);
    assertEquals(0, artOutput.exitCode);
    assertEquals(javaOutput.stdout, artOutput.stdout);
  }

  private JasminBuilder buildMethodNameConflictInHierarchy() {
    JasminBuilder builder = new JasminBuilder();
    ClassBuilder classBuilder = builder.addClass(SUPER_CLASS);
    classBuilder.addVirtualMethod("same", ImmutableList.of(), "Ljava/lang/Object;",
        "aconst_null",
        "areturn");
    classBuilder.addVirtualMethod("same", ImmutableList.of(), "Ljava/lang/String;",
        "ldc \"" + MSG + "\"",
        "areturn");
    classBuilder = builder.addClass(ANOTHER_CLASS, SUPER_CLASS);
    classBuilder.addVirtualMethod("same", ImmutableList.of(), "Ljava/lang/Object;",
        "aload_0",
        "invokespecial " + SUPER_CLASS + "/same()Ljava/lang/Object;",
        "areturn");
    classBuilder.addVirtualMethod("same", ImmutableList.of(), "Ljava/lang/String;",
        "aload_0",
        "invokespecial " + SUPER_CLASS + "/same()Ljava/lang/String;",
        "areturn");
    classBuilder = builder.addClass(CLASS_NAME);
    classBuilder.addMainMethod(
        buildCodeForVisitingDeclaredMembers(
            ImmutableList.of(
                ".limit stack 3",
                ".limit locals 5",
                "  new " + ANOTHER_CLASS,
                "  dup",
                "  invokespecial " + ANOTHER_CLASS + "/<init>()V",
                "  astore 4",
                "  ldc " + ANOTHER_CLASS,
                "  invokevirtual java/lang/Class/getDeclaredMethods()[Ljava/lang/reflect/Method;"),
            ImmutableList.of(
                "  aload 4",  // instance
                "  aconst_null",
                "  checkcast [Ljava/lang/Object;",
                "  invokevirtual java/lang/reflect/Method/invoke"
                    + "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;")));
    return builder;
  }

  @Test
  public void remainMethodNameConflictInHierarchy_keepRules() throws Exception {
    Assume.assumeTrue(ToolHelper.artSupported());
    JasminBuilder builder = buildMethodNameConflictInHierarchy();
    ProcessResult javaOutput = runOnJavaNoVerifyRaw(builder, CLASS_NAME);
    assertEquals(0, javaOutput.exitCode);

    List<String> pgConfigs = ImmutableList.of(
        "-keep class " + ANOTHER_CLASS + " {\n"
            + "  <methods>;"
            + "}\n",
        keepMainProguardConfiguration(CLASS_NAME),
        "-useuniqueclassmembernames",
        "-dontshrink");
    AndroidApp app = compileWithR8(builder, pgConfigs, null);

    DexInspector dexInspector = new DexInspector(app);
    ClassSubject sup = dexInspector.clazz(SUPER_CLASS);
    assertTrue(sup.isPresent());
    MethodSubject m1 = sup.method("java.lang.String", "same", ImmutableList.of());
    assertTrue(m1.isPresent());
    assertFalse(m1.isRenamed());
    MethodSubject m2 = sup.method("java.lang.Object", "same", ImmutableList.of());
    assertTrue(m2.isPresent());
    assertFalse(m2.isRenamed());
    assertEquals(m1.getFinalName(), m2.getFinalName());

    ClassSubject sub = dexInspector.clazz(ANOTHER_CLASS);
    assertTrue(sub.isPresent());
    MethodSubject subM1 = sub.method("java.lang.String", "same", ImmutableList.of());
    assertTrue(subM1.isPresent());
    assertFalse(subM1.isRenamed());
    MethodSubject subM2 = sub.method("java.lang.Object", "same", ImmutableList.of());
    assertTrue(subM2.isPresent());
    assertFalse(subM2.isRenamed());
    assertEquals(subM1.getFinalName(), subM2.getFinalName());

    // No matter what, overloading methods should be renamed to the same name.
    assertEquals(m1.getFinalName(), subM1.getFinalName());
    assertEquals(m2.getFinalName(), subM2.getFinalName());

    ProcessResult artOutput = runOnArtRaw(app, CLASS_NAME, dexVm);
    assertEquals(0, artOutput.exitCode);
    assertEquals(javaOutput.stdout, artOutput.stdout);
  }

  @Test
  public void remainMethodNameConflictInHierarchy_useuniqueclassmembernames() throws Exception {
    Assume.assumeTrue(ToolHelper.artSupported());
    JasminBuilder builder = buildMethodNameConflictInHierarchy();
    ProcessResult javaOutput = runOnJavaNoVerifyRaw(builder, CLASS_NAME);
    assertEquals(0, javaOutput.exitCode);

    List<String> pgConfigs = ImmutableList.of(
        keepMainProguardConfiguration(CLASS_NAME),
        "-useuniqueclassmembernames",
        "-dontshrink");
    AndroidApp app = compileWithR8(builder, pgConfigs, null);

    DexInspector dexInspector = new DexInspector(app);
    ClassSubject sup = dexInspector.clazz(SUPER_CLASS);
    assertTrue(sup.isPresent());
    MethodSubject m1 = sup.method("java.lang.String", "same", ImmutableList.of());
    assertTrue(m1.isPresent());
    assertTrue(m1.isRenamed());
    MethodSubject m2 = sup.method("java.lang.Object", "same", ImmutableList.of());
    assertTrue(m2.isPresent());
    assertTrue(m2.isRenamed());
    assertEquals(m1.getFinalName(), m2.getFinalName());

    ClassSubject sub = dexInspector.clazz(ANOTHER_CLASS);
    assertTrue(sub.isPresent());
    MethodSubject subM1 = sub.method("java.lang.String", "same", ImmutableList.of());
    assertTrue(subM1.isPresent());
    assertTrue(subM1.isRenamed());
    MethodSubject subM2 = sub.method("java.lang.Object", "same", ImmutableList.of());
    assertTrue(subM2.isPresent());
    assertTrue(subM2.isRenamed());
    assertEquals(subM1.getFinalName(), subM2.getFinalName());

    // No matter what, overloading methods should be renamed to the same name.
    assertEquals(m1.getFinalName(), subM1.getFinalName());
    assertEquals(m2.getFinalName(), subM2.getFinalName());

    ProcessResult artOutput = runOnArtRaw(app, CLASS_NAME, dexVm);
    assertEquals(0, artOutput.exitCode);
    assertEquals(javaOutput.stdout, artOutput.stdout);
  }

  @Test
  public void remainMethodNameConflictInHierarchy_useuniqueclassmembernames_overloadaggressively()
      throws Exception {
    Assume.assumeTrue(ToolHelper.artSupported());
    JasminBuilder builder = buildMethodNameConflictInHierarchy();
    ProcessResult javaOutput = runOnJavaNoVerifyRaw(builder, CLASS_NAME);
    assertEquals(0, javaOutput.exitCode);

    List<String> pgConfigs = ImmutableList.of(
        keepMainProguardConfiguration(CLASS_NAME),
        "-useuniqueclassmembernames",
        "-overloadaggressively",  // no-op
        "-dontshrink");
    AndroidApp app = compileWithR8(builder, pgConfigs, null);

    DexInspector dexInspector = new DexInspector(app);
    ClassSubject sup = dexInspector.clazz(SUPER_CLASS);
    assertTrue(sup.isPresent());
    MethodSubject m1 = sup.method("java.lang.String", "same", ImmutableList.of());
    assertTrue(m1.isPresent());
    assertTrue(m1.isRenamed());
    MethodSubject m2 = sup.method("java.lang.Object", "same", ImmutableList.of());
    assertTrue(m2.isPresent());
    assertTrue(m2.isRenamed());
    assertEquals(m1.getFinalName(), m2.getFinalName());

    ClassSubject sub = dexInspector.clazz(ANOTHER_CLASS);
    assertTrue(sub.isPresent());
    MethodSubject subM1 = sub.method("java.lang.String", "same", ImmutableList.of());
    assertTrue(subM1.isPresent());
    assertTrue(subM1.isRenamed());
    MethodSubject subM2 = sub.method("java.lang.Object", "same", ImmutableList.of());
    assertTrue(subM2.isPresent());
    assertTrue(subM2.isRenamed());
    assertEquals(subM1.getFinalName(), subM2.getFinalName());

    // No matter what, overloading methods should be renamed to the same name.
    assertEquals(m1.getFinalName(), subM1.getFinalName());
    assertEquals(m2.getFinalName(), subM2.getFinalName());

    ProcessResult artOutput = runOnArtRaw(app, CLASS_NAME, dexVm);
    assertEquals(0, artOutput.exitCode);
    assertEquals(javaOutput.stdout, artOutput.stdout);
  }

  @Test
  public void resolveMethodNameConflictInHierarchy_no_options() throws Exception {
    Assume.assumeTrue(ToolHelper.artSupported());
    JasminBuilder builder = buildMethodNameConflictInHierarchy();
    ProcessResult javaOutput = runOnJavaNoVerifyRaw(builder, CLASS_NAME);
    assertEquals(0, javaOutput.exitCode);

    List<String> pgConfigs = ImmutableList.of(
        keepMainProguardConfiguration(CLASS_NAME),
        "-dontshrink");
    AndroidApp app = compileWithR8(builder, pgConfigs, null);

    DexInspector dexInspector = new DexInspector(app);
    ClassSubject sup = dexInspector.clazz(SUPER_CLASS);
    assertTrue(sup.isPresent());
    MethodSubject m1 = sup.method("java.lang.String", "same", ImmutableList.of());
    assertTrue(m1.isPresent());
    assertTrue(m1.isRenamed());
    MethodSubject m2 = sup.method("java.lang.Object", "same", ImmutableList.of());
    assertTrue(m2.isPresent());
    assertTrue(m2.isRenamed());
    assertNotEquals(m1.getFinalName(), m2.getFinalName());

    ClassSubject sub = dexInspector.clazz(ANOTHER_CLASS);
    assertTrue(sub.isPresent());
    MethodSubject subM1 = sub.method("java.lang.String", "same", ImmutableList.of());
    assertTrue(subM1.isPresent());
    assertTrue(subM1.isRenamed());
    MethodSubject subM2 = sub.method("java.lang.Object", "same", ImmutableList.of());
    assertTrue(subM2.isPresent());
    assertTrue(subM2.isRenamed());
    // TODO(b/73149686): R8 should be able to fix this conflict w/o -overloadaggressively.
    assertEquals(subM1.getFinalName(), subM2.getFinalName());

    // TODO(b/73149686): The current impl of method minifier visits classes in a hierarchical order.
    // Hence, already renamed same names in Super are not resolvable as the internal naming state
    // uses only _name_ to resolve methods in the hierarchy.
    // // No matter what, overloading methods should be renamed to the same name.
    // assertEquals(m1.getFinalName(), subM1.getFinalName());
    // assertEquals(m2.getFinalName(), subM2.getFinalName());

    ProcessResult artOutput = runOnArtRaw(app, CLASS_NAME, dexVm);
    assertEquals(0, artOutput.exitCode);
    assertEquals(javaOutput.stdout, artOutput.stdout);
  }

  @Test
  public void remainMethodNameConflictInHierarchy_overloadaggressively() throws Exception {
    Assume.assumeTrue(ToolHelper.artSupported());
    JasminBuilder builder = buildMethodNameConflictInHierarchy();
    ProcessResult javaOutput = runOnJavaNoVerifyRaw(builder, CLASS_NAME);
    assertEquals(0, javaOutput.exitCode);

    List<String> pgConfigs = ImmutableList.of(
        keepMainProguardConfiguration(CLASS_NAME),
        "-overloadaggressively",
        "-dontshrink");
    AndroidApp app = compileWithR8(builder, pgConfigs, null);

    DexInspector dexInspector = new DexInspector(app);

    ClassSubject sup = dexInspector.clazz(SUPER_CLASS);
    assertTrue(sup.isPresent());
    MethodSubject m1 = sup.method("java.lang.String", "same", ImmutableList.of());
    assertTrue(m1.isPresent());
    assertTrue(m1.isRenamed());
    MethodSubject m2 = sup.method("java.lang.Object", "same", ImmutableList.of());
    assertTrue(m2.isPresent());
    assertTrue(m2.isRenamed());
    assertEquals(m1.getFinalName(), m2.getFinalName());

    ClassSubject sub = dexInspector.clazz(ANOTHER_CLASS);
    assertTrue(sub.isPresent());
    MethodSubject subM1 = sub.method("java.lang.String", "same", ImmutableList.of());
    assertTrue(subM1.isPresent());
    assertTrue(subM1.isRenamed());
    MethodSubject subM2 = sub.method("java.lang.Object", "same", ImmutableList.of());
    assertTrue(subM2.isPresent());
    assertTrue(subM2.isRenamed());
    assertEquals(subM1.getFinalName(), subM2.getFinalName());

    // No matter what, overloading methods should be renamed to the same name.
    assertEquals(m1.getFinalName(), subM1.getFinalName());
    assertEquals(m2.getFinalName(), subM2.getFinalName());

    ProcessResult artOutput = runOnArtRaw(app, CLASS_NAME, dexVm);
    assertEquals(0, artOutput.exitCode);
    assertEquals(javaOutput.stdout, artOutput.stdout);
  }
}
