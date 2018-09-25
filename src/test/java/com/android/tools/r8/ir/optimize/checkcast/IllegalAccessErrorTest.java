// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.checkcast;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.AsmTestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.AndroidApp;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

class NonAccessible {
  // because it's package-private
}

@RunWith(Parameterized.class)
public class IllegalAccessErrorTest extends AsmTestBase {
  private final Backend backend;

  @Parameterized.Parameters(name = "backend: {0}")
  public static Collection<Backend> data() {
    return Arrays.asList(Backend.values());
  }


  public IllegalAccessErrorTest(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void runTest() throws Exception {
    String main = "Test";
    AndroidApp input =
        buildAndroidApp(
            IllegalAccessErrorTestDump.dump(),
            ToolHelper.getClassAsBytes(NonAccessible.class));
    AndroidApp processedApp =
        compileWithR8(input, keepMainProguardConfiguration(main), null, backend);

    List<byte[]> classBytes = ImmutableList.of(
        IllegalAccessErrorTestDump.dump(),
        ToolHelper.getClassAsBytes(NonAccessible.class)
    );
    ProcessResult javaOutput = runOnJavaRaw(main, classBytes, ImmutableList.of());
    assertEquals(0, javaOutput.exitCode);

    ProcessResult output = runOnVMRaw(processedApp, main, backend);
    assertEquals(0, output.exitCode);
    if (backend == Backend.DEX) {
      assertEquals(IllegalAccessErrorTestDump.MESSAGE, output.stdout.trim());
    } else {
      assert backend == Backend.CF;
      assertEquals(javaOutput.stdout.trim(), output.stdout.trim());
      assertEquals("null", output.stdout.trim());
    }
  }
}
