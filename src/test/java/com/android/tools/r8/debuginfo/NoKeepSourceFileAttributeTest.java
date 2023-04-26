// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debuginfo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.naming.retrace.StackTrace.StackTraceLine;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class NoKeepSourceFileAttributeTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimesAndAllApiLevels().build();
  }

  public NoKeepSourceFileAttributeTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  public boolean isRuntimeWithPcAsLineNumberSupport() {
    return parameters.isDexRuntime()
        && parameters
            .getRuntime()
            .maxSupportedApiLevel()
            .isGreaterThanOrEqualTo(apiLevelWithPcAsLineNumberSupport());
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(NoKeepSourceFileAttributeTest.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertFailureWithErrorThatThrows(RuntimeException.class)
        .inspectOriginalStackTrace(
            stacktrace -> {
              List<StackTraceLine> stackTraceLines = stacktrace.getStackTraceLines();
              assertEquals(1, stackTraceLines.size());
              StackTraceLine stackTraceLine = stackTraceLines.get(0);
              if (!isRuntimeWithPcAsLineNumberSupport()) {
                assertEquals("SourceFile", stackTraceLine.fileName);
              } else {
                // VMs with native PC support and no debug info print "Unknown Source".
                // TODO(b/146565491): This will need a check for new VMs once fixed.
                assertEquals("Unknown Source", stackTraceLine.fileName);
              }
            })
        .inspectStackTrace(
            stacktrace -> {
              List<StackTraceLine> stackTraceLines = stacktrace.getStackTraceLines();
              assertEquals(1, stackTraceLines.size());
              StackTraceLine stackTraceLine = stackTraceLines.get(0);
              assertEquals("NoKeepSourceFileAttributeTest.java", stackTraceLine.fileName);
            });
  }

  @Test
  public void testNoOpt() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(NoKeepSourceFileAttributeTest.class)
        .addKeepMainRule(TestClass.class)
        // Keeping lines and not obfuscating or optimizing will retain original lines.
        .addDontObfuscate()
        .addDontOptimize()
        .addKeepAttributeLineNumberTable()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertFailureWithErrorThatThrows(RuntimeException.class)
        .inspectOriginalStackTrace(
            stacktrace -> {
              List<StackTraceLine> stackTraceLines = stacktrace.getStackTraceLines();
              assertEquals(1, stackTraceLines.size());
              StackTraceLine stackTraceLine = stackTraceLines.get(0);
              assertEquals("SourceFile", stackTraceLine.fileName);
              // The non-optimizing/obfuscating build will retain the original line info.
              assertTrue(stackTraceLine.lineNumber > 50);
            })
        .inspectStackTrace(
            stacktrace -> {
              List<StackTraceLine> stackTraceLines = stacktrace.getStackTraceLines();
              assertEquals(1, stackTraceLines.size());
              StackTraceLine stackTraceLine = stackTraceLines.get(0);
              assertEquals("NoKeepSourceFileAttributeTest.java", stackTraceLine.fileName);
              assertTrue(stackTraceLine.lineNumber > 50);
            });
  }

  static class TestClass {

    public static void main(String[] args) {
      throw new RuntimeException("My Exception!");
    }
  }
}
