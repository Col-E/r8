// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.google.common.collect.Streams;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class Regress131349148 extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection params() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public Regress131349148(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testNoInlineReflectiveOperationExceptionPreL() throws Exception {
    R8TestRunResult result =
        testForR8(parameters.getBackend())
            .addProgramClasses(TestClass.class, ClassWithCatchReflectiveOperation.class)
            .addKeepMainRule(TestClass.class)
            .setMinApi(parameters.getApiLevel())
            .compile()
            .run(parameters.getRuntime(), TestClass.class)
            .assertSuccess();
    ClassSubject classSubject = result.inspector().clazz(TestClass.class);
    boolean mainHasReflectiveOperationException =
        Streams.stream(classSubject.mainMethod().iterateTryCatches())
            .anyMatch(
                tryCatch -> tryCatch.isCatching("java.lang.ReflectiveOperationException"));
    int runtimeLevel = parameters.getApiLevel().getLevel();
    assertEquals(runtimeLevel >= AndroidApiLevel.K.getLevel(), mainHasReflectiveOperationException);
  }

  @Test
  public void testNoInlineNonExistingCatchPreL() throws Exception {
    R8TestRunResult result =
        testForR8(parameters.getBackend())
            .addProgramClasses(
                TestClassCallingMethodWithNonExisting.class,
                ClassWithCatchNonExisting.class,
                ExistingException.class)
            .addKeepMainRule(TestClassCallingMethodWithNonExisting.class)
            .addKeepRules("-dontwarn " + NonExistingException.class.getTypeName())
            .setMinApi(parameters.getApiLevel())
            .compile()
            .assertAllWarningMessagesMatch(
                containsString("required for default or static interface methods desugaring"))
            .run(parameters.getRuntime(), TestClassCallingMethodWithNonExisting.class)
            .assertSuccess();
    ClassSubject classSubject =
        result.inspector().clazz(TestClassCallingMethodWithNonExisting.class);
    boolean hasCatchHandler =
        Streams.stream(classSubject.mainMethod().iterateTryCatches()).count() > 0;
    int runtimeLevel = parameters.getApiLevel().getLevel();
    assertEquals(runtimeLevel >= AndroidApiLevel.L.getLevel(), hasCatchHandler);

  }

  static class TestClass {
    public static void main(String[] args) {
      if (args.length == 200) {
        // Never called
        ClassWithCatchReflectiveOperation.methodWithCatch();
      }
    }
  }

  static class ClassWithCatchReflectiveOperation {
    public static void methodWithCatch() {
      try {
        throw new ClassNotFoundException();
      } catch (ReflectiveOperationException e) {
        // We must use the exception, otherwise there is no move-exception that triggers the
        // verification error.
        System.out.println(e);
      }
    }
  }

  static class TestClassCallingMethodWithNonExisting {
    public static void main(String[] args) {
      if (args.length == 200) {
        // Never called
        ClassWithCatchNonExisting.methodWithCatch();
      }
    }
  }

  static class ClassWithCatchNonExisting {
    public static void methodWithCatch() {
      try {
        throw new ExistingException();
      } catch (NonExistingException e) {
        System.out.println(e);
      }
    }
  }

  static class ExistingException extends NonExistingException { }

  // We will not pass this class to R8, so the exception does not exist in the program classes.
  static class NonExistingException extends Exception { }
}
