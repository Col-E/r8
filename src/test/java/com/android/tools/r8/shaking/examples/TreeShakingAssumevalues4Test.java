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
public class TreeShakingAssumevalues4Test extends TreeShakingTest {

  @Parameters(name = "{0} minify:{1}")
  public static List<Object[]> data() {
    return defaultTreeShakingParameters();
  }

  public TreeShakingAssumevalues4Test(TestParameters parameters, MinifyMode minify) {
    super(parameters, minify);
  }

  @Override
  protected String getName() {
    return "examples/assumevalues4";
  }

  @Override
  protected String getMainClass() {
    return "assumevalues4.Assumevalues";
  }

  @Test
  public void test() throws Exception {
    runTest(
        null,
        TreeShakingAssumevalues4Test::assumevalues4CheckOutput,
        null,
        ImmutableList.of("src/test/examples/assumevalues4/keep-rules.txt"));
  }

  private static void assumevalues4CheckOutput(String output1, String output2) {
    Assert.assertEquals(
        StringUtils.lines("method0", "0", "method1", "1", "method0L", "0L", "method1L", "1L"),
        output1);
    Assert.assertEquals(
        StringUtils.lines("method0", "1", "method1", "0", "method0L", "1L", "method1L", "0L"),
        output2);
  }
}
