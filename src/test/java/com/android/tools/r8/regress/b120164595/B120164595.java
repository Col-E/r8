// Copyright (c) 2019 the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.regress.b120164595;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

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

public class B120164595 {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void regress()
      throws IOException, CompilationFailedException, ExecutionException {
     AndroidApp app =
        ToolHelper.runD8(
            D8Command.builder()
                .addClassProgramData(ToolHelper.getClassAsBytes(
                    TestClass.class), Origin.unknown()));
    TemporaryFolder temp = ToolHelper.getTemporaryFolderForTest();
    temp.create();
    Path outDex = temp.getRoot().toPath().resolve("dex.zip");
    app.writeToZip(outDex, OutputMode.DexIndexed);
    // TODO(120164595): Remove when workaround lands.
    thrown.expect(Throwable.class);
    ToolHelper.runArtNoVerificationErrors(
        ImmutableList.of(outDex.toString()),
        TestClass.class.getCanonicalName(),
        builder -> {
          builder.appendArtOption("-Xusejit:true");
        },
        DexVm.ART_9_0_0_HOST);
  }
}
