// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.examples;

import com.android.tools.r8.TestBase.MinifyMode;
import com.android.tools.r8.shaking.TreeShakingTest;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@Ignore("TODO(b/70550443): Enable once fixed.")
@RunWith(Parameterized.class)
public class TreeShakingAssumenosideeffects2Test extends TreeShakingTest {

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

  public TreeShakingAssumenosideeffects2Test(
      Frontend frontend, Backend backend, MinifyMode minify) {
    super(
        "examples/assumenosideeffects2",
        "assumenosideeffects2.Assumenosideeffects",
        frontend,
        backend,
        minify);
  }

  @Test
  public void test() throws Exception {
    runTest(
        null,
        TreeShakingAssumenosideeffects2Test::assumenosideeffects2CheckOutput,
        null,
        ImmutableList.of("src/test/examples/assumenosideeffects2/keep-rules.txt"));
  }

  private static void assumenosideeffects2CheckOutput(String output1, String output2) {
    Assert.assertEquals(StringUtils.lines("Hello, world!"), output1);
    Assert.assertEquals("", output2);
  }
}
