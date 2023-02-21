// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debuginfo.pc2pc;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.naming.retrace.StackTrace.StackTraceLine;
import com.android.tools.r8.utils.BooleanUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DifferentParameterCountSingleLineCodeTestRunner extends TestBase {

  private static final Class<?> CLASS = DifferentParameterCountSingleLineCodeTestSource.class;

  private final TestParameters parameters;
  private final boolean customSourceFile;

  @Parameterized.Parameters(name = "{0}, custom-source-file:{1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(), BooleanUtils.values());
  }

  public DifferentParameterCountSingleLineCodeTestRunner(
      TestParameters parameters, boolean customSourceFile) {
    this.parameters = parameters;
    this.customSourceFile = customSourceFile;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(CLASS)
        .addKeepMainRule(CLASS)
        // Keep all the methods but allow renaming.
        .noTreeShaking()
        .addKeepAttributeLineNumberTable()
        .addKeepAttributeSourceFile()
        .addKeepRules("-renamesourcefileattribute " + (customSourceFile ? "X" : "SourceFile"))
        .setMinApi(parameters)
        .run(parameters.getRuntime(), CLASS)
        .assertFailureWithErrorThatThrows(IllegalStateException.class)
        .inspectOriginalStackTrace(
            s -> {
              for (StackTraceLine line : s.getStackTraceLines()) {
                if (customSourceFile) {
                  // For a custom source file, all debug info must be present.
                  assertEquals("X", line.fileName);
                  assertTrue("Expected line number in: " + line, line.hasLineNumber());
                } else if (compileApiHasPcSupport()) {
                  assertEquals("Unknown Source", line.fileName);
                  assertTrue("Expected PC in: " + line, line.hasLineNumber());
                } else {
                  // Otherwise, the bare source file is printed and a line.
                  assertEquals("SourceFile", line.fileName);
                  assertTrue("Expected line number in: " + line, line.hasLineNumber());
                }
              }
              assertEquals("Expected 4 stack frames in:\n" + s, 4, s.getStackTraceLines().size());
            })
        .inspectStackTrace(
            retracedStack ->
                assertThat(
                    retracedStack,
                    StackTrace.isSame(
                        StackTrace.builder()
                            .add(makeLine("args0", 9))
                            .add(makeLine("args1", 13))
                            .add(makeLine("args2", 17))
                            .add(makeLine("main", 21))
                            .build())));
  }

  private boolean compileApiHasPcSupport() {
    return parameters.getApiLevel().isGreaterThanOrEqualTo(apiLevelWithPcAsLineNumberSupport());
  }

  private StackTraceLine makeLine(String methodName, int lineNumber) {
    return StackTraceLine.builder()
        .setClassName(typeName(CLASS))
        .setFileName(CLASS.getSimpleName() + ".java")
        .setMethodName(methodName)
        .setLineNumber(lineNumber)
        .build();
  }
}
