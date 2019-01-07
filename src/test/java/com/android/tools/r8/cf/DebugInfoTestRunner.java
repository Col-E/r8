// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf;

import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import java.nio.file.Path;
import org.junit.Test;

public class DebugInfoTestRunner extends TestBase {
  private static final Class<?> CLASS = DebugInfoTest.class;
  private static final String EXPECTED = "";

  @Test
  public void test() throws Exception {
    testForJvm().addProgramClasses(CLASS).run(CLASS).assertSuccessWithOutput(EXPECTED);

    Path out1 = temp.getRoot().toPath().resolve("out1.zip");
    builder()
        .addProgramClasses(CLASS)
        .compile()
        .writeToZip(out1)
        .run(CLASS)
        .assertSuccessWithOutput(EXPECTED);

    try {
      builder().addProgramFiles(out1).run(CLASS).assertSuccessWithOutput(EXPECTED);
      // TODO(b/77522100): Remove once fixed.
      fail();
    } catch (CompilationFailedException e) {
      // TODO(b/77522100): Remove one fixed.
      assert e.getCause().getMessage().contains("Invalid debug info");
    }
  }

  private R8TestBuilder builder() {
    return testForR8(Backend.CF)
        .debug()
        .noTreeShaking()
        .noMinification()
        .addOptionsModification(o -> o.invalidDebugInfoFatal = true);
  }
}
