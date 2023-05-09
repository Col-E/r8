// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.apimodel.ApiModelingTestHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.UnverifiableCfCodeDiagnostic;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
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
    return getTestParameters()
        .withDexRuntimes()
        .withApiLevelsStartingAtIncluding(AndroidApiLevel.K)
        .build();
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
            .setMinApi(parameters)
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
  public void testNoInlineNonExistingCatchPreLWithApiModeling() throws Exception {
    setupR8TestBuilder(
        b ->
            b.apply(ApiModelingTestHelper::enableOutliningOfMethods)
                .apply(ApiModelingTestHelper::enableStubbingOfClassesAndDisableGlobalSyntheticCheck)
                .apply(ApiModelingTestHelper::enableApiCallerIdentification),
        inspector -> {
          ClassSubject classSubject = inspector.clazz(TestClassCallingMethodWithNonExisting.class);
          boolean hasCatchHandler =
              Streams.stream(classSubject.mainMethod().iterateTryCatches()).count() > 0;
          // The catch handler does not exist in ClassWithCatchNonExisting.methodWithCatch thus we
          // assign UNKNOWN api level. As a result we do not inline.
          assertFalse(hasCatchHandler);
        });
  }

  @Test
  public void testNoInlineNonExistingCatchPreL() throws Exception {
    setupR8TestBuilder(
        b -> b.apply(ApiModelingTestHelper::disableApiModeling),
        inspector -> {
          ClassSubject classSubject = inspector.clazz(TestClassCallingMethodWithNonExisting.class);
          boolean hasCatchHandler =
              Streams.stream(classSubject.mainMethod().iterateTryCatches()).count() > 0;
          int runtimeLevel = parameters.getApiLevel().getLevel();
          assertEquals(runtimeLevel >= AndroidApiLevel.L.getLevel(), hasCatchHandler);
        });
  }

  private void setupR8TestBuilder(
      ThrowableConsumer<R8FullTestBuilder> r8TestBuilderConsumer,
      ThrowingConsumer<CodeInspector, ? extends Exception> inspect)
      throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(
            TestClassCallingMethodWithNonExisting.class,
            ClassWithCatchNonExisting.class,
            ExistingException.class)
        .addKeepMainRule(TestClassCallingMethodWithNonExisting.class)
        .addDontWarn(NonExistingException.class)
        .allowDiagnosticWarningMessages()
        .setMinApi(parameters)
        .apply(r8TestBuilderConsumer)
        .compileWithExpectedDiagnostics(
            diagnostics ->
                diagnostics.assertWarningsMatch(
                    allOf(
                        diagnosticType(UnverifiableCfCodeDiagnostic.class),
                        diagnosticMessage(
                            containsString(
                                "Unverifiable code in `void "
                                    + ClassWithCatchNonExisting.class.getTypeName()
                                    + ".methodWithCatch()`")))))
        .run(parameters.getRuntime(), TestClassCallingMethodWithNonExisting.class)
        .assertSuccess()
        .inspect(inspect);
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
