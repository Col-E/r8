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
public class TreeShakingMinifygenericTest extends TreeShakingTest {

  @Parameters(name = "{0} minify:{1}")
  public static List<Object[]> data() {
    return data(MinifyMode.withoutNone());
  }

  public TreeShakingMinifygenericTest(TestParameters parameters, MinifyMode minify) {
    super(parameters, minify);
  }

  @Override
  protected String getName() {
    return "examples/minifygeneric";
  }

  @Override
  protected String getMainClass() {
    return "minifygeneric.Minifygeneric";
  }

  @Test
  public void test() throws Exception {
    runTest(null, null, null, ImmutableList.of("src/test/examples/minifygeneric/keep-rules.txt"));
  }
}
