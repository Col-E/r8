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
public class TreeShakingAssumenosideeffects1Test extends TreeShakingTest {

  @Parameters(name = "{0} minify:{1}")
  public static List<Object[]> data() {
    return defaultTreeShakingParameters();
  }

  public TreeShakingAssumenosideeffects1Test(TestParameters parameters, MinifyMode minify) {
    super(parameters, minify);
  }

  @Override
  protected String getName() {
    return "examples/assumenosideeffects1";
  }

  @Override
  protected String getMainClass() {
    return "assumenosideeffects1.Assumenosideeffects";
  }

  @Test
  public void test() throws Exception {
    runTest(
        null,
        TreeShakingAssumenosideeffects1Test::assumenosideeffects1CheckOutput,
        null,
        ImmutableList.of("src/test/examples/assumenosideeffects1/keep-rules.txt"));
  }

  private static void assumenosideeffects1CheckOutput(String output1, String output2) {
    Assert.assertEquals(StringUtils.lines("noSideEffectVoid", "noSideEffectInt"), output1);
    Assert.assertEquals("", output2);
  }
}
