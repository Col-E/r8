// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.debuginfo;

import static com.android.tools.r8.naming.retrace.StackTrace.isSame;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.debuginfo.testclasses.SimpleCallChainClassWithOverloads;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.naming.retrace.StackTrace.StackTraceLine;
import com.android.tools.r8.transformers.ClassFileTransformer.MethodPredicate;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class OverloadWithNoLineNumberTest extends TestBase {

  private final String SOURCE_FILE_NAME = "SimpleCallChainClassWithOverloads.java";

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassFileData(
            transformer(SimpleCallChainClassWithOverloads.class)
                .removeLineNumberTable(MethodPredicate.onName("test"))
                .transform())
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(SimpleCallChainClassWithOverloads.class)
        .addKeepClassAndMembersRules(SimpleCallChainClassWithOverloads.class)
        .addKeepAttributeLineNumberTable()
        .run(parameters.getRuntime(), SimpleCallChainClassWithOverloads.class)
        .assertFailureWithErrorThatThrows(RuntimeException.class)
        .inspectStackTrace(
            (stackTrace, inspector) -> {
              StackTraceLine mainLine =
                  StackTraceLine.builder()
                      .setClassName(typeName(SimpleCallChainClassWithOverloads.class))
                      .setMethodName("main")
                      .setFileName(SOURCE_FILE_NAME)
                      .setLineNumber(10)
                      .build();
              if (parameters.isCfRuntime()
                  || parameters.getDexRuntimeVersion().isOlderThan(Version.V8_1_0)) {
                StackTraceLine testStackTraceLine =
                    StackTraceLine.builder()
                        .setClassName(typeName(SimpleCallChainClassWithOverloads.class))
                        .setMethodName("test")
                        .setFileName(SOURCE_FILE_NAME)
                        .build();
                assertThat(
                    stackTrace,
                    isSame(
                        StackTrace.builder()
                            // TODO(b/251677184): Stack trace lines should still be distinguishable
                            //  even if there are no original line numbers to map back two.
                            .add(testStackTraceLine)
                            .add(testStackTraceLine)
                            .add(mainLine)
                            .build()));
              } else {
                assertThat(
                    stackTrace,
                    isSame(
                        StackTrace.builder()
                            // TODO(b/251677184): Strange that we are able to distinguish when using
                            //  pc mapping.
                            .add(
                                StackTraceLine.builder()
                                    .setClassName(typeName(SimpleCallChainClassWithOverloads.class))
                                    .setMethodName("test")
                                    .setFileName(SOURCE_FILE_NAME)
                                    .setLineNumber(11)
                                    .build())
                            .add(
                                StackTraceLine.builder()
                                    .setClassName(typeName(SimpleCallChainClassWithOverloads.class))
                                    .setMethodName("test")
                                    .setFileName(SOURCE_FILE_NAME)
                                    .setLineNumber(4)
                                    .build())
                            .add(mainLine)
                            .build()));
              }
            });
  }
}
