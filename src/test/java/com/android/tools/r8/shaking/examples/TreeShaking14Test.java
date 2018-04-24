// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.examples;

import com.android.tools.r8.TestBase.MinifyMode;
import com.android.tools.r8.shaking.TreeShakingTest;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.ClassSubject;
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
public class TreeShaking14Test extends TreeShakingTest {

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

  public TreeShaking14Test(Frontend frontend, Backend backend, MinifyMode minify) {
    super("examples/shaking14", "shaking14.Shaking", frontend, backend, minify);
  }

  @Test
  public void test() throws Exception {
    runTest(
        TreeShaking14Test::shaking14EnsureRightStaticMethodsLive,
        null,
        null,
        ImmutableList.of("src/test/examples/shaking14/keep-rules.txt"));
  }

  private static void shaking14EnsureRightStaticMethodsLive(DexInspector inspector) {
    ClassSubject superclass = inspector.clazz("shaking14.Superclass");
    Assert.assertFalse(superclass.method("int", "aMethod", ImmutableList.of("int")).isPresent());
    Assert.assertFalse(
        superclass.method("double", "anotherMethod", ImmutableList.of("double")).isPresent());
    ClassSubject subclass = inspector.clazz("shaking14.Subclass");
    Assert.assertTrue(subclass.method("int", "aMethod", ImmutableList.of("int")).isPresent());
    Assert.assertTrue(
        subclass.method("double", "anotherMethod", ImmutableList.of("double")).isPresent());
  }
}
