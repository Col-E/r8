// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf;

import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DebugInfoTestRunner extends TestBase {
  private static final Class<?> CLASS = DebugInfoTest.class;
  private static final String EXPECTED = "";

  @Parameters(name = "{0}")
  public static Backend[] data() {
    return Backend.values();
  }

  private final Backend backend;

  public DebugInfoTestRunner(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void test() throws Exception {
    if (backend == Backend.CF) {
      testForJvm().addProgramClasses(CLASS).run(CLASS).assertSuccessWithOutput(EXPECTED);
    }

    // Compile the input with R8 and run.
    Path out = temp.getRoot().toPath().resolve("out.zip");
    builder()
        .addProgramClasses(CLASS)
        .compile()
        .writeToZip(out)
        .run(CLASS)
        .assertSuccessWithOutput(EXPECTED);

    if (backend == Backend.CF) {
      // If first compilation was to CF, then compile and run it again.
      builder().addProgramFiles(out).run(CLASS).assertSuccessWithOutput(EXPECTED);
    }
  }

  private R8TestBuilder builder() {
    return testForR8(backend)
        .debug()
        .noTreeShaking()
        .noMinification()
        .addOptionsModification(o -> o.invalidDebugInfoFatal = true);
  }
}
