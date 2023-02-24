// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.retrace;

import static com.android.tools.r8.naming.retrace.StackTrace.isSame;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.naming.retrace.StackTrace.StackTraceLine;
import com.android.tools.r8.naming.retrace.testclasses.DesugarInterfaceInstanceLambdaRetrace;
import com.android.tools.r8.naming.retrace.testclasses.DesugarInterfaceInstanceLambdaRetrace.ConsumerDesugarLambda;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DesugarPrivateLambdaRetraceTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  private final String fileName = "DesugarInterfaceInstanceLambdaRetrace.java";

  public StackTrace expectedStackTrace =
      StackTrace.builder()
          .add(
              StackTraceLine.builder()
                  .setClassName(typeName(ConsumerDesugarLambda.class))
                  .setMethodName("lambda$foo$0")
                  .setFileName(fileName)
                  .setLineNumber(16)
                  .build())
          .add(
              StackTraceLine.builder()
                  .setClassName(typeName(DesugarInterfaceInstanceLambdaRetrace.Main.class))
                  .setMethodName("method1")
                  .setFileName(fileName)
                  .setLineNumber(26)
                  .build())
          .add(
              StackTraceLine.builder()
                  .setClassName(typeName(ConsumerDesugarLambda.class))
                  .setMethodName("foo")
                  .setFileName(fileName)
                  .setLineNumber(13)
                  .build())
          .add(
              StackTraceLine.builder()
                  .setClassName(typeName(DesugarInterfaceInstanceLambdaRetrace.Main.class))
                  .setMethodName("main")
                  .setFileName(fileName)
                  .setLineNumber(30)
                  .build())
          .build();

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClasses(DesugarInterfaceInstanceLambdaRetrace.class)
        .run(TestRuntime.getDefaultCfRuntime(), DesugarInterfaceInstanceLambdaRetrace.Main.class)
        .inspectStackTrace(stackTrace -> assertThat(stackTrace, isSame(expectedStackTrace)));
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .setMode(CompilationMode.DEBUG)
        .addInnerClasses(DesugarInterfaceInstanceLambdaRetrace.class)
        .setMinApi(parameters)
        .internalEnableMappingOutput()
        .run(parameters.getRuntime(), DesugarInterfaceInstanceLambdaRetrace.Main.class)
        .assertFailureWithErrorThatThrows(NullPointerException.class)
        .inspectStackTrace(stackTrace -> assertThat(stackTrace, isSame(expectedStackTrace)));
  }
}
