// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.TestBase;
import org.junit.Test;

/**
 * This tests that we produce valid code when having normal-flow with exceptional edges in blocks.
 * We might perform optimizations that add operations (dup, swap, etc.) before and after
 * instructions that lie on the boundary of the exception table that is generated for a basic block.
 * If live-ranges are minimized this could produce VerifyErrors. TODO(b/119771771) Will fail if
 * shorten live ranges without shorten exception table range.
 */
public class TryRangeTestRunner extends TestBase {

  @Test
  public void test() throws Exception {
    testForR8(Backend.CF)
        .addProgramClasses(TryRangeTest.class)
        .addKeepMainRule(TryRangeTest.class)
        .setMode(CompilationMode.RELEASE)
        .minification(false)
        .noTreeShaking()
        .enableInliningAnnotations()
        .addOptionsModification(o -> o.testing.disallowLoadStoreOptimization = true)
        .run(TryRangeTest.class)
        .assertSuccess();
  }
}
