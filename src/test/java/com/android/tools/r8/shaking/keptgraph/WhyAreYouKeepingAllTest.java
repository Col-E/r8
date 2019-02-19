// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.keptgraph;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.StringUtils;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;

/**
 * Run compiling R8 with R8 using a match-all -whyareyoukeeping rule to check that it does not cause
 * compilation to fail.
 */
public class WhyAreYouKeepingAllTest extends TestBase {

  private static final Path MAIN_KEEP = Paths.get("src/main/keep.txt");

  private static final String WHY_ARE_YOU_KEEPING_ALL = StringUtils.lines(
      "-whyareyoukeeping class ** { *; }",
      "-whyareyoukeeping @interface ** { *; }"
  );

  @Test
  public void test() throws Throwable {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    testForR8(Backend.CF)
        .addProgramFiles(ToolHelper.R8_WITH_RELOCATED_DEPS_JAR)
        .addKeepRuleFiles(MAIN_KEEP)
        .addKeepRules(WHY_ARE_YOU_KEEPING_ALL)
        .redirectStdOut(new PrintStream(baos))
        .compile();
    assertThat(baos.toString(), containsString("referenced in keep rule"));
    // TODO(b/124655065): We should always know the reason for keeping.
    assertThat(baos.toString(), containsString("kept for unknown reasons"));
  }
}
