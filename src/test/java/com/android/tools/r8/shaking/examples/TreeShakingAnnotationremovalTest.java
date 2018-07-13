// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.examples;

import com.android.tools.r8.TestBase.MinifyMode;
import com.android.tools.r8.shaking.TreeShakingTest;
import com.android.tools.r8.utils.dexinspector.ClassSubject;
import com.android.tools.r8.utils.dexinspector.DexInspector;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TreeShakingAnnotationremovalTest extends TreeShakingTest {

  @Parameters(name = "mode:{0}-{1} minify:{2}")
  public static Collection<Object[]> data() {
    List<Object[]> parameters = new ArrayList<>();
    for (MinifyMode minify : MinifyMode.values()) {
      parameters.add(new Object[] {Frontend.JAR, Backend.CF, minify});
      parameters.add(new Object[] {Frontend.JAR, Backend.DEX, minify});
      parameters.add(new Object[] {Frontend.DEX, Backend.DEX, minify});
    }
    return parameters;
  }

  public TreeShakingAnnotationremovalTest(Frontend frontend, Backend backend, MinifyMode minify) {
    super(
        "examples/annotationremoval",
        "annotationremoval.Annotationremoval",
        frontend,
        backend,
        minify);
  }

  @Test
  public void testKeeprules() throws Exception {
    runTest(
        TreeShakingAnnotationremovalTest::annotationRemovalHasNoInnerClassAnnotations,
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
  public void testKeepruleskeepinnerannotation() throws Exception {
    runTest(
        TreeShakingAnnotationremovalTest::annotationRemovalHasAllInnerClassAnnotations,
        null,
        null,
        ImmutableList.of("src/test/examples/annotationremoval/keep-rules-keep-innerannotation.txt"),
        options -> {
          // To ensure that enclosing method and inner class attributes are kept even on classes
          // that are not explicitly mentioned by a keep rule.
          options.forceProguardCompatibility = true;
        });
  }

  private static void annotationRemovalHasNoInnerClassAnnotations(DexInspector inspector) {
    ClassSubject outer = inspector.clazz("annotationremoval.OuterClass");
    Assert.assertTrue(outer.isPresent());
    Assert.assertTrue(outer.getDexClass().getInnerClasses().isEmpty());
    ClassSubject inner = inspector.clazz("annotationremoval.OuterClass$InnerClass");
    Assert.assertTrue(inner.isPresent());
    Assert.assertNull(inner.getDexClass().getEnclosingMethod());
    Assert.assertTrue(inner.getDexClass().getInnerClasses().isEmpty());
    ClassSubject anonymous = inspector.clazz("annotationremoval.OuterClass$1");
    Assert.assertTrue(anonymous.isPresent());
    Assert.assertNull(anonymous.getDexClass().getEnclosingMethod());
    Assert.assertTrue(anonymous.getDexClass().getInnerClasses().isEmpty());
    ClassSubject local = inspector.clazz("annotationremoval.OuterClass$1LocalMagic");
    Assert.assertTrue(local.isPresent());
    Assert.assertNull(local.getDexClass().getEnclosingMethod());
    Assert.assertTrue(local.getDexClass().getInnerClasses().isEmpty());
  }

  private static void annotationRemovalHasAllInnerClassAnnotations(DexInspector inspector) {
    ClassSubject outer = inspector.clazz("annotationremoval.OuterClass");
    Assert.assertTrue(outer.isPresent());
    Assert.assertFalse(outer.getDexClass().getInnerClasses().isEmpty());
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
