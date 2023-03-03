// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.examples;

import static org.junit.Assert.assertFalse;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.shaking.TreeShakingTest;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TreeShaking18Test extends TreeShakingTest {

  @Parameters(name = "{0} minify:{1}")
  public static List<Object[]> data() {
    return defaultTreeShakingParameters();
  }

  public TreeShaking18Test(TestParameters parameters, MinifyMode minify) {
    super(parameters, minify);
  }

  @Override
  protected String getName() {
    return "examples/shaking18";
  }

  @Override
  protected String getMainClass() {
    return "shaking18.Shaking";
  }

  @Test
  public void test() throws Exception {
    runTest(
        TreeShaking18Test::unusedRemoved,
        null,
        null,
        ImmutableList.of("src/test/examples/shaking18/keep-rules.txt"));
  }

  private static void unusedRemoved(CodeInspector inspector) {
    assertFalse(
        "DerivedUnused should be removed", inspector.clazz("shaking18.DerivedUnused").isPresent());
  }
}
