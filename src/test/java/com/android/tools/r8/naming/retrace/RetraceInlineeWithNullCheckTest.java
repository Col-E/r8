// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.retrace;

import static com.android.tools.r8.naming.retrace.StackTrace.isSame;
import static com.android.tools.r8.naming.retrace.StackTrace.isSameExceptForLineNumbers;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.naming.retrace.StackTrace.StackTraceLine;
import com.android.tools.r8.utils.BooleanUtils;
import java.util.List;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RetraceInlineeWithNullCheckTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean throwReceiverNpe;

  @Parameters(name = "{0}, throwReceiverNpe: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build(),
        BooleanUtils.values());
  }

  public StackTrace expectedStackTrace;

  @Before
  public void setup() throws Exception {
    // Get the expected stack trace by running on the JVM.
    TestBuilder<? extends SingleTestRunResult<?>, ?> testBuilder =
        testForRuntime(parameters).addProgramClasses(Caller.class, Foo.class);
    SingleTestRunResult<?> runResult;
    if (throwReceiverNpe) {
      runResult = testBuilder.run(parameters.getRuntime(), Caller.class, "foo");
    } else {
      runResult = testBuilder.run(parameters.getRuntime(), Caller.class);
    }
    if (parameters.isCfRuntime()) {
      expectedStackTrace = runResult.map(StackTrace::extractFromJvm);
    } else {
      expectedStackTrace =
          StackTrace.extractFromArt(runResult.getStdErr(), parameters.asDexRuntime().getVm());
    }
  }

  @Test
  public void testR8() throws Exception {
    R8FullTestBuilder r8FullTestBuilder =
        testForR8(parameters.getBackend())
            .addInnerClasses(getClass())
            .addKeepMainRule(Caller.class)
            .addKeepAttributeLineNumberTable()
            .addKeepAttributeSourceFile()
            .setMinApi(parameters.getApiLevel())
            .enableInliningAnnotations()
            .enableExperimentalMapFileVersion();
    R8TestRunResult runResult;
    if (throwReceiverNpe) {
      runResult = r8FullTestBuilder.run(parameters.getRuntime(), Caller.class, "foo");
    } else {
      runResult = r8FullTestBuilder.run(parameters.getRuntime(), Caller.class);
    }
    runResult
        .assertFailureWithErrorThatThrows(NullPointerException.class)
        .inspectStackTrace(
            (stackTrace, codeInspector) -> {
              if (throwReceiverNpe && canUseJavaUtilObjectsRequireNonNull(parameters)) {
                StackTrace requireNonNullFrame =
                    StackTrace.builder().add(stackTrace.get(0)).build();
                assertThat(
                    requireNonNullFrame,
                    isSameExceptForLineNumbers(
                        StackTrace.builder()
                            .add(
                                StackTraceLine.builder()
                                    .setClassName(Objects.class.getTypeName())
                                    .setMethodName("requireNonNull")
                                    .setFileName("Objects.java")
                                    .build())
                            .build()));

                StackTrace stackTraceWithoutRequireNonNull =
                    StackTrace.builder().add(stackTrace).remove(0).build();
                assertThat(stackTraceWithoutRequireNonNull, isSame(expectedStackTrace));
              } else {
                assertThat(stackTrace, isSame(expectedStackTrace));
              }
            });
  }

  static class Foo {
    @NeverInline
    Object notInlinable() {
      System.out.println("Hello, world!");
      throw new NullPointerException("Foo");
    }

    Object inlinable() {
      return notInlinable();
    }
  }

  static class Caller {
    @NeverInline
    static void caller(Foo f) {
      f.inlinable();
    }

    public static void main(String[] args) {
      caller(args.length == 0 ? new Foo() : null);
    }
  }
}
