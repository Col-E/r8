// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.TestBase;
import org.junit.Test;

/**
 * This tests that we produce valid code when having normal-flow with exceptional edges in blocks.
 * We might perform optimizations that add stack-operations (dup, swap, etc.) before and after
 * instructions that lie on the boundary of the exception table that is generated for a basic block:
 *
 * <pre>
 *   Code:
 *      0: invokestatic  #16                 // Method create:()Lcom/android/tools/r8/cf/CloserTest;
 *      3: dup
 *      4: dup
 *      5: astore_1
 *      ...
 *   Exception table:
 *      from    to  target type
 *      3       9   24     Class java/lang/Throwable
 *      ...
 *   StackMap table:
 *   StackMapTable: number_of_entries = 4
 *         frame_type = 255
 *         offset_delta=21
 *         locals=[top, class com/android/tools/r8/cf/CloserTest]
 *         stack=[class java/lang/Throwable]
 * </pre>
 *
 * If we produce something like this, the JVM verifier will throw a VerifyError on @bci: 3 since we
 * have not stored CloserTest in locals[1] (that happens in @bci 5), as described in the
 * StackMapTable. This is because the exception handler starts at @bci 3 and not later.
 * TODO(b/122445224)
 */
public class CloserTestRunner extends TestBase {

  @Test
  public void test() throws Exception {
    testForR8(Backend.CF)
        .addProgramClasses(CloserTest.class)
        .addKeepMainRule(CloserTest.class)
        .setMode(CompilationMode.RELEASE)
        .minification(false)
        .noTreeShaking()
        .enableInliningAnnotations()
        .compile()
        .run(CloserTest.class)
        .assertFailure();
  }
}
