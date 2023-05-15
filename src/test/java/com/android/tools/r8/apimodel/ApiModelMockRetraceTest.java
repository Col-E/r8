// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.verifyThat;
import static com.android.tools.r8.naming.retrace.StackTrace.containsLine;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.naming.retrace.StackTrace.EquivalenceWithoutFileNameAndLineNumber;
import com.android.tools.r8.naming.retrace.StackTrace.StackTraceLine;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelMockRetraceTest extends TestBase {

  private final AndroidApiLevel mockLevel = AndroidApiLevel.M;

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private boolean addToBootClasspath() {
    return parameters.isCfRuntime()
        || parameters.getRuntime().maxSupportedApiLevel().isGreaterThanOrEqualTo(mockLevel);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, ProgramClass.class)
        .addLibraryClasses(LibraryClass.class)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters)
        .addAndroidBuildVersion()
        .apply(ApiModelingTestHelper::enableStubbingOfClassesAndDisableGlobalSyntheticCheck)
        .apply(setMockApiLevelForClass(LibraryClass.class, mockLevel))
        .addKeepMainRule(Main.class)
        .addKeepClassRules(ProgramClass.class)
        .compile()
        .inspect(this::inspect)
        .applyIf(addToBootClasspath(), b -> b.addBootClasspathClasses(LibraryClass.class))
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput);
  }

  private void inspect(CodeInspector inspector) {
    verifyThat(inspector, parameters, LibraryClass.class)
        .stubbedBetween(AndroidApiLevel.L_MR1, mockLevel);
  }

  private void checkOutput(SingleTestRunResult<?> runResult) {
    if (!addToBootClasspath()) {
      runResult.assertFailureWithErrorThatThrows(NoClassDefFoundError.class);
      return;
    }
    StackTraceLine clinitFrame =
        StackTraceLine.builder()
            .setClassName(typeName(LibraryClass.class))
            .setMethodName("<clinit>")
            .build();
    EquivalenceWithoutFileNameAndLineNumber equivalence =
        EquivalenceWithoutFileNameAndLineNumber.get();
    runResult.inspectOriginalStackTrace(
        originalStackTrace ->
            assertThat(originalStackTrace, containsLine(clinitFrame, equivalence)));
    if (parameters.isCfRuntime()) {
      runResult
          .assertFailureWithErrorThatThrows(ExceptionInInitializerError.class)
          .assertFailureWithErrorThatThrows(ArithmeticException.class)
          .inspectStackTrace(
              stackTrace -> assertThat(stackTrace, containsLine(clinitFrame, equivalence)));
    } else {
      runResult
          .applyIf(
              addToBootClasspath(),
              result ->
                  result
                      .assertFailureWithErrorThatThrows(ExceptionInInitializerError.class)
                      .assertFailureWithErrorThatThrows(ArithmeticException.class),
              result -> result.assertFailureWithErrorThatThrows(NoClassDefFoundError.class))
          .inspectStackTrace(
              stackTrace -> assertThat(stackTrace, containsLine(clinitFrame, equivalence)));
    }
  }

  // Only present from 23.
  public static class LibraryClass {

    public static final int VALUE;

    static {
      VALUE = 2 / (System.currentTimeMillis() == 0 ? 1 : 0);
    }
  }

  public static class ProgramClass extends LibraryClass {}

  public static class Main {

    public static void main(String[] args) throws Exception {
      // Trigger a CL init that will either throw in the program stub or the library CL init.
      Class.forName(LibraryClass.class.getName());
    }
  }
}
