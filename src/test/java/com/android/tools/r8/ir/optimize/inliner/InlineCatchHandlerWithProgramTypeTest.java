// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.inliner;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.Streams;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Simple test to ensure that we do inline program defined types used in catch handler guards. */
@RunWith(Parameterized.class)
public class InlineCatchHandlerWithProgramTypeTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public InlineCatchHandlerWithProgramTypeTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(InlineCatchHandlerWithProgramTypeTest.class)
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Done...")
        .inspect(this::checkInlined);
  }

  private void checkInlined(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(TestClass.class);
    ClassSubject exceptionSubject = inspector.clazz(MyException.class);
    boolean mainHasInlinedCatchHandler =
        Streams.stream(classSubject.mainMethod().iterateTryCatches())
            .anyMatch(tryCatch -> tryCatch.isCatching(exceptionSubject.getFinalName()));
    assertTrue(mainHasInlinedCatchHandler);
  }

  static class MyException extends RuntimeException {}

  static class TestClass {

    public static void main(String[] args) {
      if (args.length == 200) {
        // Never called
        ClassWithCatchHandler.methodWithCatch();
      }
      System.out.println("Done...");
    }
  }

  static class ClassWithCatchHandler {

    @NeverInline
    public static void maybeThrow() {
      if (System.nanoTime() > 0) {
        throw new MyException();
      }
    }

    public static void methodWithCatch() {
      try {
        maybeThrow();
      } catch (MyException e) {
        System.out.println(e.getClass().getName());
      }
    }
  }
}
