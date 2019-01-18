// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.examples;

import com.android.tools.r8.shaking.TreeShakingTest;
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
public class TreeShaking11Test extends TreeShakingTest {

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

  public TreeShaking11Test(Frontend frontend, Backend backend, MinifyMode minify) {
    super("examples/shaking11", "shaking11.Shaking", frontend, backend, minify);
  }

  @Test
  public void testKeeprules() throws Exception {
    runTest(
        TreeShaking11Test::shaking11OnlyOneClassKept,
        null,
        null,
        ImmutableList.of("src/test/examples/shaking11/keep-rules.txt"));
  }

  @Test
  public void testKeepruleskeepmethod() throws Exception {
    runTest(
        TreeShaking11Test::shaking11BothMethodsKept,
        null,
        null,
        ImmutableList.of("src/test/examples/shaking11/keep-rules-keep-method.txt"));
  }

  private static void shaking11OnlyOneClassKept(CodeInspector codeInspector) {
    Assert.assertFalse(codeInspector.clazz("shaking11.Subclass").isPresent());
    Assert.assertTrue(codeInspector.clazz("shaking11.SubclassWithMethod").isPresent());
  }

  private static void shaking11BothMethodsKept(CodeInspector codeInspector) {
    Assert.assertFalse(
        codeInspector
            .clazz("shaking11.Subclass")
            .method("void", "aMethod", Collections.emptyList())
            .isPresent());
    Assert.assertTrue(
        codeInspector
            .clazz("shaking11.SuperClass")
            .method("void", "aMethod", Collections.emptyList())
            .isPresent());
    Assert.assertTrue(
        codeInspector
            .clazz("shaking11.SubclassWithMethod")
            .method("void", "aMethod", Collections.emptyList())
            .isPresent());
  }
}
