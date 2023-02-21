// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debuginfo;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexDebugEvent.SetFile;
import com.android.tools.r8.smali.SmaliBuilder;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DebugSetFileSmaliTest extends TestBase {

  private final TestParameters parameters;

  public DebugSetFileSmaliTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Parameters(name = "{0}")
  public static TestParametersCollection parameters() {
    return TestParameters.builder().withDexRuntimes().withApiLevel(AndroidApiLevel.B).build();
  }

  private static final String CLASS_NAME = "Test";
  private static final String CLASS_SOURCE_FILE = "Test.java";
  private static final String DEBUG_SET_FILE = "SomeFile.java";

  @Test
  public void test() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(CLASS_NAME);
    builder.setSourceFile(CLASS_SOURCE_FILE);
    builder.addMainMethod(
        2,
        ".line 1",
        "    sget-object         v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "    const-string        v1, \"Hello, world!\"",
        // If the following invoke is not present legacy VMs fail with "invalid debug stream"!?
        "    invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->println(Ljava/lang/String;)V",
        ".source \"" + DEBUG_SET_FILE + "\"",
        ".line 42",
        "    const               v0, 0",
        "    throw v0");

    testForD8(parameters.getBackend())
        .setMinApi(parameters)
        .addProgramDexFileData(builder.compile())
        .compile()
        .inspect(
            inspector -> {
              assertFalse(
                  Arrays.stream(
                          inspector
                              .clazz(CLASS_NAME)
                              .mainMethod()
                              .getMethod()
                              .getCode()
                              .asDexCode()
                              .getDebugInfo()
                              .asEventBasedInfo()
                              .events)
                      .anyMatch(e -> e instanceof SetFile));
            })
        .run(parameters.getRuntime(), CLASS_NAME)
        .assertFailureWithErrorThatMatches(
            containsString("at Test.main(" + CLASS_SOURCE_FILE + ":42)"));
  }
}
