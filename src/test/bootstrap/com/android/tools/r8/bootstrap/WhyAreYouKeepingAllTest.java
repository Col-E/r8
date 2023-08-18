// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.bootstrap;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;

import com.android.tools.r8.JdkClassFileProvider;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.dex.Marker.Backend;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Run compiling R8 with R8 using a match-all -whyareyoukeeping rule to check that it does not cause
 * compilation to fail.
 */
@RunWith(Parameterized.class)
public class WhyAreYouKeepingAllTest extends TestBase {

  private static final Path MAIN_KEEP = Paths.get(ToolHelper.SOURCE_DIR + "/main/keep.txt");

  private static final String WHY_ARE_YOU_KEEPING_ALL = StringUtils.lines(
      "-whyareyoukeeping class ** { *; }",
      "-whyareyoukeeping @interface ** { *; }"
  );

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public WhyAreYouKeepingAllTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void test() throws Throwable {
    testForR8(Backend.CF)
        .addProgramFiles(ToolHelper.getR8WithRelocatedDeps())
        .addLibraryProvider(JdkClassFileProvider.fromSystemJdk())
        .addKeepRuleFiles(MAIN_KEEP)
        .addKeepRules(WHY_ARE_YOU_KEEPING_ALL)
        .collectStdout()
        .compile()
        .assertStdoutThatMatches(containsString("referenced in keep rule"))
        // TODO(b/124655065): We should always know the reason for keeping.
        // It is OK if this starts failing while the kept-graph API is incomplete, in which case
        // replace the 'not(containsString(' by just 'containsString('.
        .assertStdoutThatMatches(not(containsString("kept for unknown reasons")));
  }
}
