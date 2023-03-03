// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.examples;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.shaking.TreeShakingTest;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.Collections;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TreeShaking6Test extends TreeShakingTest {

  @Parameters(name = "{0} minify:{1}")
  public static List<Object[]> data() {
    return defaultTreeShakingParameters();
  }

  public TreeShaking6Test(TestParameters parameters, MinifyMode minify) {
    super(parameters, minify);
  }

  @Override
  protected String getName() {
    return "examples/shaking6";
  }

  @Override
  protected String getMainClass() {
    return "shaking6.Shaking";
  }

  @Test
  public void testKeepjustamethodonint() throws Exception {
    runTest(
        TreeShaking6Test::hasOnlyIntJustAMethod,
        null,
        null,
        ImmutableList.of("src/test/examples/shaking6/keep-justAMethod-OnInt.txt"));
  }

  @Test
  public void testKeepjustamethodpublic() throws Exception {
    runTest(
        TreeShaking6Test::hasNoPrivateJustAMethod,
        null,
        null,
        ImmutableList.of("src/test/examples/shaking6/keep-justAMethod-public.txt"));
  }

  @Test
  public void testKeepnonpublic() throws Exception {
    runTest(
        TreeShaking6Test::hasNoPublicMethodsButPrivate,
        null,
        null,
        ImmutableList.of("src/test/examples/shaking6/keep-non-public.txt"));
  }

  @Test
  public void testKeeppublic() throws Exception {
    runTest(
        TreeShaking6Test::hasNoPrivateMethods,
        null,
        null,
        ImmutableList.of("src/test/examples/shaking6/keep-public.txt"));
  }

  private static void hasNoPublicMethodsButPrivate(CodeInspector inspector) {
    inspector.forAllClasses(
        clazz ->
            clazz.forAllMethods(
                method -> {
                  if (!method.isStatic() && !method.isFinal()) {
                    Assert.assertTrue(!method.getMethod().accessFlags.isPublic());
                  }
                }));
    Assert.assertTrue(
        inspector
            .clazz("shaking6.Superclass")
            .method("void", "justAMethod", Collections.emptyList())
            .isPresent());
  }

  private static void hasOnlyIntJustAMethod(CodeInspector inspector) {
    Assert.assertFalse(
        inspector
            .clazz("shaking6.Superclass")
            .method("void", "justAMethod", Collections.emptyList())
            .isPresent());
    ClassSubject subclass = inspector.clazz("shaking6.Subclass");
    Assert.assertTrue(subclass.isPresent());
    Assert.assertFalse(subclass.method("void", "justAMethod", Collections.emptyList()).isPresent());
    Assert.assertTrue(
        subclass.method("void", "justAMethod", Collections.singletonList("int")).isPresent());
    Assert.assertFalse(
        subclass.method("void", "justAMethod", Collections.singletonList("boolean")).isPresent());
    Assert.assertFalse(
        subclass.method("int", "justAMethod", Collections.singletonList("double")).isPresent());
  }

  private static void hasNoPrivateJustAMethod(CodeInspector inspector) {
    Assert.assertFalse(
        inspector
            .clazz("shaking6.Superclass")
            .method("void", "justAMethod", Collections.emptyList())
            .isPresent());
    ClassSubject subclass = inspector.clazz("shaking6.Subclass");
    Assert.assertTrue(subclass.isPresent());
    Assert.assertTrue(subclass.method("void", "justAMethod", Collections.emptyList()).isPresent());
    Assert.assertTrue(
        subclass.method("void", "justAMethod", Collections.singletonList("int")).isPresent());
    Assert.assertTrue(
        subclass.method("void", "justAMethod", Collections.singletonList("boolean")).isPresent());
    Assert.assertFalse(
        subclass.method("int", "justAMethod", Collections.singletonList("double")).isPresent());
  }

  private static void hasNoPrivateMethods(CodeInspector inspector) {
    inspector.forAllClasses(
        clazz ->
            clazz.forAllMethods(
                method -> Assert.assertTrue(!method.getMethod().accessFlags.isPrivate())));
  }
}
