// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.retrace;

import static com.android.tools.r8.naming.retrace.StackTrace.isSame;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoMethodStaticizing;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RetraceInlineeWithNullCheckInlinedTest extends TestBase {

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
    expectedStackTrace =
        testForRuntime(parameters)
            .addProgramClasses(Caller.class, Foo.class)
            .run(parameters.getRuntime(), Caller.class, getArgs())
            .getStackTrace();
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Caller.class)
        .addKeepAttributeLineNumberTable()
        .addKeepAttributeSourceFile()
        .setMinApi(parameters.getApiLevel())
        .enableInliningAnnotations()
        .enableNoMethodStaticizingAnnotations()
        .run(parameters.getRuntime(), Caller.class, getArgs())
        .assertFailureWithErrorThatThrows(NullPointerException.class)
        .inspectStackTrace(
            (stackTrace, inspector) -> {
              ClassSubject callerClass = inspector.clazz(Caller.class);
              assertThat(callerClass, isPresent());
              MethodSubject outerCaller = callerClass.uniqueMethodWithOriginalName("outerCaller");
              assertThat(outerCaller, isPresentAndRenamed());
              assertThat(stackTrace, isSame(expectedStackTrace));
            });
  }

  private String[] getArgs() {
    return throwReceiverNpe ? new String[] {"Foo"} : new String[0];
  }

  static class Foo {
    @NeverInline
    @NoMethodStaticizing
    Object notInlinable() {
      System.out.println("Hello, world!");
      throw new NullPointerException("Foo");
    }

    Object inlinable() {
      return notInlinable();
    }
  }

  static class Caller {

    static void caller(Foo f) {
      Object inlinable = f.inlinable();
      System.out.println(inlinable == null ? "null" : "some");
    }

    @NeverInline
    public static void outerCaller(Foo f) {
      // caller should be inlined here as is. When inlinable is inlined into caller, it is done so
      // without synthesizing a null check since the call notInlineable will throw an NPE if not
      // staticized. We have this outer caller to check that stack traces still work correctly
      // when the bit on throwing NPE is inlined.
      caller(f);
    }

    public static void main(String[] args) {
      outerCaller(args.length == 0 ? new Foo() : null);
    }
  }
}
