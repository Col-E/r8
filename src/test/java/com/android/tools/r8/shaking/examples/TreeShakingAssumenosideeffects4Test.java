// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.examples;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.shaking.TreeShakingTest;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TreeShakingAssumenosideeffects4Test extends TreeShakingTest {

  @Parameters(name = "mode:{0}minify:{1}")
  public static List<Object[]> data() {
    return defaultTreeShakingParameters();
  }

  public TreeShakingAssumenosideeffects4Test(TestParameters parameters, MinifyMode minify) {
    super(parameters, minify);
  }

  @Override
  protected String getName() {
    return "examples/assumenosideeffects4";
  }

  @Override
  protected String getMainClass() {
    return "assumenosideeffects4.Assumenosideeffects";
  }

  @Test
  public void test() throws Exception {
    runTest(
        null,
        TreeShakingAssumenosideeffects4Test::assumenosideeffects4CheckOutput,
        null,
        ImmutableList.of("src/test/examples/assumenosideeffects4/keep-rules.txt"));
  }

  private static void assumenosideeffects4CheckOutput(String output1, String output2) {
    Assert.assertEquals(
        StringUtils.lines("method0", "0", "method1", "1", "method0L", "0L", "method1L", "1L"),
        output1);
    Assert.assertEquals(StringUtils.lines("1", "0", "1L", "0L"), output2);
  }
}
