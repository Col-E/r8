// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.forceproguardcompatibility;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.compatproguard.CompatProguardCommandBuilder;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.ClassSubject;
import com.android.tools.r8.utils.DexInspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import org.junit.Test;

public class ForceProguardCompatibilityTest extends TestBase {
  private void test(Class mainClass, Class mentionedClass, boolean arrayClass,
      boolean forceProguardCompatibility)
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
    if (arrayClass) {
      MethodSubject defaultInitializer = clazz.method(MethodSignature.initializer(new String[]{}));
      assertFalse(defaultInitializer.isPresent());
    } else {
      MethodSubject defaultInitializer = clazz.method(MethodSignature.initializer(new String[]{}));
      assertEquals(forceProguardCompatibility, defaultInitializer.isPresent());
    }
  }

  @Test
  public void testKeepDefaultInitializer() throws Exception {
    test(TestMain.class, TestMain.MentionedClass.class, false, true);
    test(TestMain.class, TestMain.MentionedClass.class, false, false);
  }

  @Test
  public void testKeepDefaultInitializerArrayType() throws Exception {
    test(TestMainArrayType.class, TestMainArrayType.MentionedClass.class, true, true);
    test(TestMainArrayType.class, TestMainArrayType.MentionedClass.class, true, false);
  }

  private void runAnnotationsTest(boolean forceProguardCompatibility, boolean keepAnnotations) throws Exception {
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
        ImmutableList.of(keepMainProguardConfiguration(mainClass, true, false)));
    builder.addProguardConfiguration(
        ImmutableList.of("-keep class " + annotationClass.getCanonicalName() + " { }"));
    if (keepAnnotations) {
      builder.addProguardConfiguration(ImmutableList.of("-keepattributes *Annotation*"));
    }

    DexInspector inspector = new DexInspector(ToolHelper.runR8(builder.build()));
    assertTrue(inspector.clazz(mainClass.getCanonicalName()).isPresent());
    ClassSubject clazz = inspector.clazz(getJavacGeneratedClassName(mentionedClassWithAnnotations));
    assertTrue(clazz.isPresent());

    assertEquals(!keepAnnotations && forceProguardCompatibility,
        clazz.annotation("dalvik.annotation.EnclosingClass").isPresent());
    assertEquals(!keepAnnotations && forceProguardCompatibility,
        clazz.annotation("dalvik.annotation.InnerClass").isPresent());
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
}
