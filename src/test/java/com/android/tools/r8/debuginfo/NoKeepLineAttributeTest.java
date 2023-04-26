// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debuginfo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.naming.retrace.StackTrace.StackTraceLine;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class NoKeepLineAttributeTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        // Only run the test on VMs that have native pc support.
        .withDexRuntimesStartingFromExcluding(Version.V7_0_0)
        .withAllApiLevels()
        .build();
  }

  public NoKeepLineAttributeTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(NoKeepLineAttributeTest.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertFailureWithErrorThatThrows(RuntimeException.class)
        .inspectStackTrace(
            stacktrace -> {
              List<StackTraceLine> stackTraceLines = stacktrace.getStackTraceLines();
              assertEquals(1, stackTraceLines.size());
              StackTraceLine stackTraceLine = stackTraceLines.get(0);
              // The frame will always have a line as the VM is reporting the PC.
              assertTrue(stackTraceLine.hasLineNumber());
              if (parameters.getApiLevel().isLessThan(apiLevelWithPcAsLineNumberSupport())) {
                // If the compile-time API is before native support then no line info is present.
                // The "line" will be the PC and thus small.
                assertTrue(stackTraceLine.lineNumber < 10);
              } else {
                // If the compile-time API is after native support then the compiler will retain and
                // emit the mapping from PC to original line. Here line 50 is to ensure it is not a
                // low PC value.
                assertTrue(stackTraceLine.lineNumber > 50);
              }
            });
  }

  static class TestClass {

    public static void main(String[] args) {
      throw new RuntimeException("My Exception!");
    }
  }
}
