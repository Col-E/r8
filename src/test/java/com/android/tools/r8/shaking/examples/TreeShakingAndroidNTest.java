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
public class TreeShakingAndroidNTest extends TreeShakingTest {

  @Parameters(name = "mode:{0}-{1} minify:{2}")
  public static List<Object[]> data() {
    return data(new Frontend[] {Frontend.JAR}, MinifyMode.values());
  }

  public TreeShakingAndroidNTest(Frontend frontend, TestParameters parameters, MinifyMode minify) {
    super(frontend, parameters, minify);
  }

  @Override
  protected String getName() {
    return "examplesAndroidN/shaking";
  }

  @Override
  protected String getMainClass() {
    return "shaking.Shaking";
  }

  @Test
  public void test() throws Exception {
    runTest(null, null, null, ImmutableList.of("src/test/examplesAndroidN/shaking/keep-rules.txt"));
  }
}
