// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.examples;

import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.shaking.TreeShakingTest;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TreeShakingInliningTest extends TreeShakingTest {

  @Parameters(name = "{0} minify:{1}")
  public static List<Object[]> data() {
    return defaultTreeShakingParameters();
  }

  public TreeShakingInliningTest(TestParameters parameters, MinifyMode minify) {
    super(parameters, minify);
  }

  @Override
  protected String getName() {
    return "examples/inlining";
  }

  @Override
  protected String getMainClass() {
    return "inlining.Inlining";
  }

  @Test
  public void testKeeprules() throws Exception {
    runTest(null, null, null, ImmutableList.of("src/test/examples/inlining/keep-rules.txt"));
  }

  @Test
  public void testKeeprulesdiscard() throws Exception {
    // On the cf backend, we don't inline into constructors, see: b/136250031
    List<String> keepRules =
        getParameters().isCfRuntime()
            ? ImmutableList.of("src/test/examples/inlining/keep-rules-discard.txt")
            : ImmutableList.of(
                "src/test/examples/inlining/keep-rules-discard.txt",
                "src/test/examples/inlining/keep-rules-discard-constructor.txt");
    runTest(null, null, null, keepRules, null, TestCompilerBuilder::allowStderrMessages, null);
  }
}
