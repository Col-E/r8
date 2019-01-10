// Copyright (c) 2019 the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.regress.b120164595;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.matchers.JUnitMatchers.containsString;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import org.junit.Test;

/**
 * Regression test for art issue with multi catch-handlers.
 * The problem is that if d8/r8 creates the same target address for two different exceptions,
 * art will use that address to check if it was already handled.
 */
class TestClass {
  public static void main(String[] args) {
    for (int i = 0; i < 20000; i++) {
      toBeOptimized();
    }
  }

  private static void toBeOptimized() {
    try {
      willThrow();
    } catch (IllegalStateException | NullPointerException e) {
      if (e instanceof NullPointerException) {
        return;
      }
      throw new Error("Expected NullPointerException");
    }
  }

  private static void willThrow() {
    throw new NullPointerException();
  }
}

public class B120164595 extends TestBase {
  @Test
  public void testD8()
      throws IOException, CompilationFailedException {
    TestCompileResult d8Result = testForD8().addProgramClasses(TestClass.class).compile();
    checkArt(d8Result);
  }

  @Test
  public void testR8()
      throws IOException, CompilationFailedException {
    TestCompileResult r8Result = testForR8(Backend.DEX)
        .addProgramClasses(TestClass.class)
        .addKeepClassAndMembersRules(TestClass.class)
        .compile();
    checkArt(r8Result);
  }

  private void checkArt(TestCompileResult result) throws IOException {
    ProcessResult artResult = runOnArtRaw(
        result.app,
        TestClass.class.getCanonicalName(),
        builder -> {
          builder.appendArtOption("-Xusejit:true");
        },
        DexVm.ART_9_0_0_HOST
    );
    // TODO(120164595): Remove when workaround lands.
    assertNotEquals(artResult.exitCode, 0);
    assertTrue(artResult.stderr.contains("Expected NullPointerException"));
  }
}
