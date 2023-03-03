// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.examples;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.shaking.TreeShakingTest;
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
public class TreeShaking5Test extends TreeShakingTest {

  @Parameters(name = "{0} minify:{1}")
  public static List<Object[]> data() {
    return defaultTreeShakingParameters();
  }

  public TreeShaking5Test(TestParameters parameters, MinifyMode minify) {
    super(parameters, minify);
  }

  @Override
  protected String getName() {
    return "examples/shaking5";
  }

  @Override
  protected String getMainClass() {
    return "shaking5.Shaking";
  }

  @Test
  public void test() throws Exception {
    runTest(
        TreeShaking5Test::shaking5Inspection,
        null,
        null,
        ImmutableList.of("src/test/examples/shaking5/keep-rules.txt"));
  }

  private static void shaking5Inspection(CodeInspector inspector) {
    Assert.assertFalse(
        inspector
            .clazz("shaking5.Superclass")
            .method("void", "virtualMethod", Collections.emptyList())
            .isPresent());
  }
}
