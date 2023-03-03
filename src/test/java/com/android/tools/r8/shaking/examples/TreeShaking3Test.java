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
public class TreeShaking3Test extends TreeShakingTest {

  @Parameters(name = "{0} minify:{1}")
  public static List<Object[]> data() {
    return defaultTreeShakingParameters();
  }

  public TreeShaking3Test(TestParameters parameters, MinifyMode minify) {
    super(parameters, minify);
  }

  @Override
  protected String getName() {
    return "examples/shaking3";
  }

  @Override
  protected String getMainClass() {
    return "shaking3.Shaking";
  }

  @Test
  public void testKeepbytag() throws Exception {
    runTest(
        TreeShaking3Test::shaking3HasNoClassB,
        null,
        null,
        ImmutableList.of("src/test/examples/shaking3/keep-by-tag.txt"));
  }

  @Test
  public void testKeepbytagdefault() throws Exception {
    runTest(
        TreeShaking3Test::shaking3HasNoClassB,
        null,
        null,
        ImmutableList.of("src/test/examples/shaking3/keep-by-tag-default.txt"));
  }

  @Test
  public void testKeepbytagonmethod() throws Exception {
    runTest(
        TreeShaking3Test::shaking3HasNoClassB,
        null,
        null,
        ImmutableList.of("src/test/examples/shaking3/keep-by-tag-on-method.txt"));
  }

  @Test
  public void testKeepbytagviainterface() throws Exception {
    runTest(
        TreeShaking3Test::shaking3HasNoClassB,
        null,
        null,
        ImmutableList.of("src/test/examples/shaking3/keep-by-tag-via-interface.txt"));
  }

  @Test
  public void testKeepbytagwithpattern() throws Exception {
    runTest(
        TreeShaking3Test::shaking3HasNoClassB,
        null,
        null,
        ImmutableList.of("src/test/examples/shaking3/keep-by-tag-with-pattern.txt"));
  }

  @Test
  public void testKeepnoabstractclasses() throws Exception {
    runTest(
        TreeShaking3Test::shaking3HasNoPrivateClass,
        null,
        null,
        ImmutableList.of("src/test/examples/shaking3/keep-no-abstract-classes.txt"));
  }

  private static void shaking3HasNoPrivateClass(CodeInspector inspector) {
    Assert.assertTrue(inspector.clazz("shaking3.B").isPresent());
    Assert.assertFalse(inspector.clazz("shaking3.AnAbstractClass").isPresent());
  }

  private static void shaking3HasNoClassB(CodeInspector inspector) {
    Assert.assertFalse(inspector.clazz("shaking3.B").isPresent());
    ClassSubject classA = inspector.clazz("shaking3.A");
    Assert.assertTrue(classA.isPresent());
    Assert.assertFalse(classA.method("void", "unused", ImmutableList.of()).isPresent());
  }
}
