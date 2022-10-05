// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.retrace;

import static com.android.tools.r8.naming.retrace.StackTrace.isSame;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.KeepUnusedReturnValue;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RetraceInlineeWithNoSuchMethodErrorTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameter(1)
  public boolean throwReceiverNpe;

  @Parameters(name = "{0}, throwReceiverNpe: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  public StackTrace expectedStackTrace;

  @Before
  public void setup() throws Exception {
    // Get the expected stack trace by running on the JVM.
    TestBuilder<? extends SingleTestRunResult<?>, ?> testBuilder =
        testForRuntime(parameters)
            .addProgramClasses(Caller.class)
            .addProgramClassFileData(getFoo());
    SingleTestRunResult<?> runResult;
    if (throwReceiverNpe) {
      runResult =
          testBuilder
              .run(parameters.getRuntime(), Caller.class, "foo")
              .assertFailureWithErrorThatThrows(NullPointerException.class);
    } else {
      runResult =
          testBuilder
              .run(parameters.getRuntime(), Caller.class)
              .assertFailureWithErrorThatThrows(NoSuchMethodError.class);
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
            .addProgramClasses(Caller.class)
            .addProgramClassFileData(getFoo())
            .addKeepMainRule(Caller.class)
            .addKeepAttributeLineNumberTable()
            .addKeepAttributeSourceFile()
            .setMinApi(parameters.getApiLevel())
            .enableInliningAnnotations()
            .enableKeepUnusedReturnValueAnnotations()
            .enableExperimentalMapFileVersion();
    R8TestRunResult runResult;
    if (throwReceiverNpe) {
      runResult =
          r8FullTestBuilder
              .run(parameters.getRuntime(), Caller.class, "foo")
              .assertFailureWithErrorThatThrows(NullPointerException.class);
    } else {
      runResult =
          r8FullTestBuilder
              .run(parameters.getRuntime(), Caller.class)
              .assertFailureWithErrorThatThrows(NoSuchMethodError.class);
    }
    runResult.inspectStackTrace(
        (stackTrace, codeInspector) -> {
          assertThat(stackTrace, isSame(expectedStackTrace));
          ClassSubject fooClass = codeInspector.clazz(Foo.class);
          assertThat(fooClass, isPresent());
          // We are not inlining this because resolution fails
          assertThat(fooClass.uniqueMethodWithOriginalName("inlinable"), isPresent());
        });
  }

  private static byte[] getFoo() throws Exception {
    return transformer(Foo.class).removeMethodsWithName("foo").transform();
  }

  public static class Foo {

    /* Removed on input */
    public Object foo() {
      throw new RuntimeException("Will be removed");
    }

    @KeepUnusedReturnValue
    Object inlinable() {
      return foo();
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
