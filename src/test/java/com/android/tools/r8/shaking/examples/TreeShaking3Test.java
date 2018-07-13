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
public class TreeShaking3Test extends TreeShakingTest {

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

  public TreeShaking3Test(Frontend frontend, Backend backend, MinifyMode minify) {
    super("examples/shaking3", "shaking3.Shaking", frontend, backend, minify);
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

  private static void shaking3HasNoPrivateClass(DexInspector inspector) {
    Assert.assertTrue(inspector.clazz("shaking3.B").isPresent());
    Assert.assertFalse(inspector.clazz("shaking3.AnAbstractClass").isPresent());
  }

  private static void shaking3HasNoClassB(DexInspector inspector) {
    Assert.assertFalse(inspector.clazz("shaking3.B").isPresent());
    ClassSubject classA = inspector.clazz("shaking3.A");
    Assert.assertTrue(classA.isPresent());
    Assert.assertFalse(classA.method("void", "unused", ImmutableList.of()).isPresent());
  }
}
