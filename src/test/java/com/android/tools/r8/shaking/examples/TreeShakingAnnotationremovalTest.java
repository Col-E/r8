// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.examples;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.shaking.TreeShakingTest;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TreeShakingAnnotationremovalTest extends TreeShakingTest {

  @Parameters(name = "{0} minify:{1}")
  public static List<Object[]> data() {
    return defaultTreeShakingParameters();
  }

  public TreeShakingAnnotationremovalTest(TestParameters parameters, MinifyMode minify) {
    super(parameters, minify);
  }

  @Override
  protected String getName() {
    return "examples/annotationremoval";
  }

  @Override
  protected String getMainClass() {
    return "annotationremoval.Annotationremoval";
  }

  @Test
  public void testKeepRules() throws Exception {
    runTest(
        this::annotationRemovalHasNoInnerClassAnnotations,
        null,
        null,
        ImmutableList.of("src/test/examples/annotationremoval/keep-rules.txt"),
        options -> {
          // To ensure that enclosing method and inner class attributes are kept even on classes
          // that are not explicitly mentioned by a keep rule.
          options.forceProguardCompatibility = true;
        });
  }

  @Test
  public void testKeepRulesKeepInnerAnnotation() throws Exception {
    runTest(
        this::annotationRemovalHasAllInnerClassAnnotations,
        null,
        null,
        ImmutableList.of("src/test/examples/annotationremoval/keep-rules-keep-innerannotation.txt"),
        options -> {
          // To ensure that enclosing method and inner class attributes are kept even on classes
          // that are not explicitly mentioned by a keep rule.
          options.forceProguardCompatibility = true;
        });
  }

  private void annotationRemovalHasNoInnerClassAnnotations(CodeInspector inspector) {
    ClassSubject outer = inspector.clazz("annotationremoval.OuterClass");
    Assert.assertTrue(outer.isPresent());
    Assert.assertTrue(outer.getDexProgramClass().getInnerClasses().isEmpty());
    ClassSubject inner = inspector.clazz("annotationremoval.OuterClass$InnerClass");
    Assert.assertTrue(inner.isPresent());
    Assert.assertNull(inner.getDexProgramClass().getEnclosingMethodAttribute());
    Assert.assertTrue(inner.getDexProgramClass().getInnerClasses().isEmpty());
    ClassSubject anonymous = inspector.clazz("annotationremoval.OuterClass$1");
    Assert.assertTrue(anonymous.isPresent());
    Assert.assertNull(anonymous.getDexProgramClass().getEnclosingMethodAttribute());
    Assert.assertTrue(anonymous.getDexProgramClass().getInnerClasses().isEmpty());
    ClassSubject local = inspector.clazz("annotationremoval.OuterClass$1LocalMagic");
    Assert.assertTrue(local.isPresent());
    Assert.assertNull(local.getDexProgramClass().getEnclosingMethodAttribute());
    Assert.assertTrue(local.getDexProgramClass().getInnerClasses().isEmpty());
  }

  private void annotationRemovalHasAllInnerClassAnnotations(CodeInspector inspector) {
    ClassSubject outer = inspector.clazz("annotationremoval.OuterClass");
    Assert.assertTrue(outer.isPresent());
    Assert.assertFalse(outer.getDexProgramClass().getInnerClasses().isEmpty());
    ClassSubject inner = inspector.clazz("annotationremoval.OuterClass$InnerClass");
    Assert.assertTrue(inner.isPresent());
    Assert.assertTrue(inner.isMemberClass());
    Assert.assertFalse(inner.isAnonymousClass());
    Assert.assertFalse(inner.isLocalClass());
    ClassSubject anonymous = inspector.clazz("annotationremoval.OuterClass$1");
    Assert.assertTrue(anonymous.isPresent());
    Assert.assertTrue(anonymous.isAnonymousClass());
    Assert.assertFalse(anonymous.isMemberClass());
    Assert.assertFalse(anonymous.isLocalClass());
    ClassSubject local = inspector.clazz("annotationremoval.OuterClass$1LocalMagic");
    Assert.assertTrue(local.isPresent());
    Assert.assertTrue(local.isLocalClass());
    Assert.assertFalse(local.isMemberClass());
    Assert.assertFalse(local.isAnonymousClass());
  }
}
