// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static com.android.tools.r8.naming.retrace.StackTrace.isSame;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.naming.retrace.StackTrace.StackTraceLine;
import com.android.tools.r8.transformers.ClassFileTransformer.MethodPredicate;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.google.common.collect.Sets;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InlineFunctionInPrunedClassTest extends TestBase {

  private static final String NEW_SOURCE_FILE = "SourceFileA.java";
  private static final String ORIGINAL_SOURCE_FILE = "InlineFunctionInPrunedClassTest.java";

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClassFileData(getAWithCustomSourceFile(), getMainWithStaticPosition())
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(NullPointerException.class)
        .inspectStackTrace(this::checkExpectedStackTrace);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassFileData(getAWithCustomSourceFile(), getMainWithStaticPosition())
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .addKeepAttributeSourceFile()
        .addKeepAttributeLineNumberTable()
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(NullPointerException.class)
        .inspectStackTrace(
            (stackTrace, inspector) -> {
              // Ensure we have inlined the A class and it is not in the output.
              assertEquals(
                  Sets.newHashSet(typeName(Main.class)),
                  inspector.allClasses().stream()
                      .map(FoundClassSubject::getFinalName)
                      .collect(Collectors.toSet()));
              checkExpectedStackTrace(stackTrace);
            });
  }

  private void checkExpectedStackTrace(StackTrace stackTrace) {
    assertThat(
        stackTrace,
        isSame(
            StackTrace.builder()
                .add(
                    StackTraceLine.builder()
                        .setClassName(typeName(A.class))
                        .setMethodName("foo")
                        .setFileName(NEW_SOURCE_FILE)
                        .setLineNumber(1)
                        .build())
                .add(
                    StackTraceLine.builder()
                        .setClassName(typeName(Main.class))
                        .setMethodName("main")
                        .setFileName(ORIGINAL_SOURCE_FILE)
                        .setLineNumber(1)
                        .build())
                .build()));
  }

  private static byte[] getAWithCustomSourceFile() throws Exception {
    return transformer(A.class)
        .setSourceFile(NEW_SOURCE_FILE)
        .setPredictiveLineNumbering(MethodPredicate.all(), 1)
        .transform();
  }

  private static byte[] getMainWithStaticPosition() throws Exception {
    return transformer(Main.class).setPredictiveLineNumbering(MethodPredicate.all(), 1).transform();
  }

  public static class A {

    public static void foo() {
      throw new NullPointerException();
    }
  }

  public static class Main {

    public static void main(String[] args) {
      A.foo();
    }
  }
}
