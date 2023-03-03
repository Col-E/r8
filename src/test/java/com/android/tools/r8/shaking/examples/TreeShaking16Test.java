// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.examples;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.shaking.TreeShakingTest;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TreeShaking16Test extends TreeShakingTest {

  @Parameters(name = "{0} minify:{1}")
  public static List<Object[]> data() {
    return data(MinifyMode.withoutNone());
  }

  public TreeShaking16Test(TestParameters parameters, MinifyMode minify) {
    super(parameters, minify);
  }

  @Override
  protected String getName() {
    return "examples/shaking16";
  }

  @Override
  protected String getMainClass() {
    return "shaking16.Shaking";
  }

  @Test
  public void testKeeprules1() throws Exception {
    runTest(null, null, null, ImmutableList.of("src/test/examples/shaking16/keep-rules-1.txt"));
  }

  @Test
  public void testKeeprules2() throws Exception {
    runTest(null, null, null, ImmutableList.of("src/test/examples/shaking16/keep-rules-2.txt"));
  }
}
