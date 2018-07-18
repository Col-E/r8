// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.examples;

import com.android.tools.r8.TestBase.MinifyMode;
import com.android.tools.r8.shaking.TreeShakingTest;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TreeShaking6Test extends TreeShakingTest {

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

  public TreeShaking6Test(Frontend frontend, Backend backend, MinifyMode minify) {
    super("examples/shaking6", "shaking6.Shaking", frontend, backend, minify);
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
