// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debuginfo;

import static com.android.tools.r8.naming.retrace.StackTrace.isSame;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.naming.retrace.StackTrace.StackTraceLine;
import com.android.tools.r8.smali.SmaliBuilder;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ZeroLineEntryTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withApiLevel(AndroidApiLevel.B).build();
  }

  public ZeroLineEntryTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    SmaliBuilder smali = new SmaliBuilder();
    smali.addClass("Test");
    smali.setSourceFile("Test.java");
    smali.addMainMethod(
        0,
        ".line 42",
        "invoke-static {}, Ljava/lang/System;->nanoTime()J",
        ".line 0",
        "new-instance v0, Ljava/lang/RuntimeException;",
        "invoke-direct {v0}, Ljava/lang/RuntimeException;-><init>()V",
        ".line 123",
        "throw v0");

    testForRuntime(parameters)
        .addProgramDexFileData(smali.compile())
        .run(parameters.getRuntime(), "Test")
        .assertFailureWithErrorThatThrows(RuntimeException.class)
        .inspectStackTrace(this::checkStackTrace);
  }

  private void checkStackTrace(StackTrace stacktrace) {
    assertThat(
        stacktrace,
        isSame(
            StackTrace.builder()
                .add(
                    StackTraceLine.builder()
                        .setLineNumber(0)
                        .setMethodName("main")
                        .setClassName("Test")
                        .setFileName("Test.java")
                        .build())
                .build()));
  }
}
