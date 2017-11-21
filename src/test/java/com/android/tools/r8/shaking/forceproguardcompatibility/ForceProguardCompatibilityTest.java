// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.forceproguardcompatibility;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.compatproguard.CompatProguardCommandBuilder;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.ClassSubject;
import com.android.tools.r8.utils.DexInspector.MethodSubject;
import com.android.tools.r8.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class ForceProguardCompatibilityTest extends TestBase {
  // Actually running Proguard should only be during development.
  private final boolean RUN_PROGUARD = false;

  private void test(Class mainClass, Class mentionedClass, boolean forceProguardCompatibility)
      throws Exception {
    String proguardConfig = keepMainProguardConfiguration(mainClass, true, false);
    DexInspector inspector = new DexInspector(
        compileWithR8(
            ImmutableList.of(mainClass, mentionedClass),
            proguardConfig,
            options -> options.forceProguardCompatibility = forceProguardCompatibility));
    assertTrue(inspector.clazz(mainClass.getCanonicalName()).isPresent());
    ClassSubject clazz = inspector.clazz(getJavacGeneratedClassName(mentionedClass));
    assertTrue(clazz.isPresent());
    MethodSubject defaultInitializer = clazz.method(MethodSignature.initializer(new String[]{}));
    assertEquals(forceProguardCompatibility, defaultInitializer.isPresent());
  }

  @Test
  public void testKeepDefaultInitializer() throws Exception {
    test(TestMain.class, TestMain.MentionedClass.class, true);
    test(TestMain.class, TestMain.MentionedClass.class, false);
  }

  @Test
  public void testKeepDefaultInitializerArrayType() throws Exception {
    test(TestMainArrayType.class, TestMainArrayType.MentionedClass.class, true);
    test(TestMainArrayType.class, TestMainArrayType.MentionedClass.class, false);
  }

  private void runAnnotationsTest(boolean forceProguardCompatibility, boolean keepAnnotations)
      throws Exception {
    R8Command.Builder builder =
        new CompatProguardCommandBuilder(forceProguardCompatibility, false);
    // Add application classes including the annotation class.
    Class mainClass = TestMain.class;
    Class mentionedClassWithAnnotations = TestMain.MentionedClassWithAnnotation.class;
    Class annotationClass = TestAnnotation.class;
    builder.addProgramFiles(ToolHelper.getClassFileForTestClass(mainClass));
    builder.addProgramFiles(ToolHelper.getClassFileForTestClass(TestMain.MentionedClass.class));
    builder.addProgramFiles(ToolHelper.getClassFileForTestClass(mentionedClassWithAnnotations));
    builder.addProgramFiles(ToolHelper.getClassFileForTestClass(annotationClass));
    // Keep main class and the annotation class.
    builder.addProguardConfiguration(
        ImmutableList.of(keepMainProguardConfiguration(mainClass, true, false)), Origin.unknown());
    builder.addProguardConfiguration(
        ImmutableList.of("-keep class " + annotationClass.getCanonicalName() + " { }"),
        Origin.unknown());
    if (keepAnnotations) {
      builder.addProguardConfiguration(ImmutableList.of("-keepattributes *Annotation*"),
          Origin.unknown());
    }

    DexInspector inspector = new DexInspector(ToolHelper.runR8(builder.build()));
    assertTrue(inspector.clazz(mainClass.getCanonicalName()).isPresent());
    ClassSubject clazz = inspector.clazz(getJavacGeneratedClassName(mentionedClassWithAnnotations));
    assertTrue(clazz.isPresent());

    // The test contains only a member class so the enclosing-method attribute will be null.
    assertEquals(
        !keepAnnotations && forceProguardCompatibility,
        !clazz.getDexClass().getInnerClasses().isEmpty());
    assertEquals(forceProguardCompatibility || keepAnnotations,
        clazz.annotation(annotationClass.getCanonicalName()).isPresent());
  }

  @Test
  public void testAnnotations() throws Exception {
    runAnnotationsTest(true, true);
    runAnnotationsTest(true, false);
    runAnnotationsTest(false, true);
    runAnnotationsTest(false, false);
  }

  private void runDefaultConstructorTest(boolean forceProguardCompatibility,
      Class<?> testClass, boolean hasDefaultConstructor) throws Exception {
    R8Command.Builder builder = new CompatProguardCommandBuilder(forceProguardCompatibility, false);
    builder.addProgramFiles(ToolHelper.getClassFileForTestClass(testClass));
    List<String> proguardConfig = ImmutableList.of(
        "-keep class " + testClass.getCanonicalName() + " {",
        "  public void method();",
        "}");
    builder.addProguardConfiguration(proguardConfig, Origin.unknown());
    DexInspector inspector = new DexInspector(ToolHelper.runR8(builder.build()));
    ClassSubject clazz = inspector.clazz(getJavacGeneratedClassName(testClass));
    assertTrue(clazz.isPresent());
    assertEquals(forceProguardCompatibility && hasDefaultConstructor,
        clazz.init(ImmutableList.of()).isPresent());

    if (RUN_PROGUARD) {
      Path proguardedJar = File.createTempFile("proguarded", ".jar", temp.getRoot()).toPath();
      Path proguardConfigFile = File.createTempFile("proguard", ".config", temp.getRoot()).toPath();
      FileUtils.writeTextFile(proguardConfigFile, proguardConfig);
      ToolHelper.runProguard(jarTestClasses(testClass), proguardedJar, proguardConfigFile);
    }
  }

  @Test
  public void testDefaultConstructor() throws Exception {
    runDefaultConstructorTest(true, TestClassWithDefaultConstructor.class, true);
    runDefaultConstructorTest(true, TestClassWithoutDefaultConstructor.class, false);
    runDefaultConstructorTest(false, TestClassWithDefaultConstructor.class, true);
    runDefaultConstructorTest(false, TestClassWithoutDefaultConstructor.class, false);
  }

  public void testCheckCast(boolean forceProguardCompatibility, Class mainClass,
      Class instantiatedClass, boolean containsCheckCast)
      throws Exception {
    R8Command.Builder builder = new CompatProguardCommandBuilder(forceProguardCompatibility, false);
    builder.addProgramFiles(ToolHelper.getClassFileForTestClass(mainClass));
    builder.addProgramFiles(ToolHelper.getClassFileForTestClass(instantiatedClass));
    List<String> proguardConfig = ImmutableList.of(
        "-keep class " + mainClass.getCanonicalName() + " {",
        "  public static void main(java.lang.String[]);",
        "}",
        "-dontobfuscate");
    builder.addProguardConfiguration(proguardConfig, Origin.unknown());

    DexInspector inspector = new DexInspector(ToolHelper.runR8(builder.build()));
    assertTrue(inspector.clazz(getJavacGeneratedClassName(mainClass)).isPresent());
    ClassSubject clazz = inspector.clazz(getJavacGeneratedClassName(instantiatedClass));
    assertEquals(containsCheckCast, clazz.isPresent());
    assertEquals(containsCheckCast, clazz.isPresent());
    if (clazz.isPresent()) {
      assertEquals(forceProguardCompatibility && containsCheckCast, !clazz.isAbstract());
    }

    if (RUN_PROGUARD) {
      Path proguardedJar = File.createTempFile("proguarded", ".jar", temp.getRoot()).toPath();
      Path proguardConfigFile = File.createTempFile("proguard", ".config", temp.getRoot()).toPath();
      FileUtils.writeTextFile(proguardConfigFile, proguardConfig);
      ToolHelper.runProguard(jarTestClasses(ImmutableList.of(mainClass, instantiatedClass)),
          proguardedJar, proguardConfigFile);
      Set<String> classesAfterProguard = readClassesInJar(proguardedJar);
      assertTrue(classesAfterProguard.contains(mainClass.getCanonicalName()));
      assertEquals(
          containsCheckCast, classesAfterProguard.contains(instantiatedClass.getCanonicalName()));
    }
  }

  @Test
  public void checkCastTest() throws Exception {
    testCheckCast(true, TestMainWithCheckCast.class, TestClassWithDefaultConstructor.class, true);
    testCheckCast(
        true, TestMainWithoutCheckCast.class, TestClassWithDefaultConstructor.class, false);
    testCheckCast(
        false, TestMainWithCheckCast.class, TestClassWithDefaultConstructor.class, true);
    testCheckCast(
        false, TestMainWithoutCheckCast.class, TestClassWithDefaultConstructor.class, false);
  }
}
