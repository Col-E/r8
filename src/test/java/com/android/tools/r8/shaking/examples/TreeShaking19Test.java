// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.examples;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.shaking.TreeShakingTest;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TreeShaking19Test extends TreeShakingTest {

  @Parameters(name = "{0} minify:{1}")
  public static List<Object[]> data() {
    return defaultTreeShakingParameters();
  }

  public TreeShaking19Test(TestParameters parameters, MinifyMode minify) {
    super(parameters, minify);
  }

  @Override
  protected String getName() {
    return "examples/shaking19";
  }

  @Override
  protected String getMainClass() {
    return "shaking19.Shaking";
  }

  @Test
  public void test() throws Exception {
    runTest(
        TreeShaking19Test::unusedRemoved,
        null,
        null,
        ImmutableList.of("src/test/examples/shaking19/keep-rules.txt"),
        // Disable vertical class merging to prevent A from being merged into B.
        options -> options.getVerticalClassMergerOptions().disable());
  }

  private static void unusedRemoved(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz("shaking19.Shaking$A");
    assertThat(clazz, isPresent());

    MethodSubject method = clazz.method("void", "m", ImmutableList.of());
    assertThat(method, not(isPresent()));
  }
}
