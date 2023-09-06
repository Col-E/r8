// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.diagnostics;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.errors.DuplicateTypeInProgramAndLibraryDiagnostic;
import com.android.tools.r8.errors.DuplicateTypesDiagnostic;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ProgramAndLibraryDuplicatesTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("Hello, world");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDefaultRuntimes()
        .withMinimumApiLevel()
        .enableApiLevelsForCf()
        .build();
  }

  public ProgramAndLibraryDuplicatesTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addInnerClasses(ProgramAndLibraryDuplicatesTest.class)
        .addDefaultRuntimeLibrary(parameters)
        .addLibraryClasses(MyFunction.class)
        .setMinApi(parameters)
        .compile()
        .assertNoMessages()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(ProgramAndLibraryDuplicatesTest.class)
        .addDefaultRuntimeLibrary(parameters)
        .addLibraryClasses(MyFunction.class)
        .setMinApi(parameters)
        .addKeepMainRule(TestClass.class)
        .allowDiagnosticInfoMessages()
        .compileWithExpectedDiagnostics(
            diagnostics ->
                diagnostics
                    .assertOnlyInfos()
                    .assertInfosMatch(
                        allOf(
                            diagnosticType(DuplicateTypesDiagnostic.class),
                            diagnosticType(DuplicateTypeInProgramAndLibraryDiagnostic.class),
                            diagnosticMessage(containsString(typeName(MyFunction.class))))))
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  interface MyFunction<T, R> {
    R apply(T arg);
  }

  static class TestClass {

    public static void run(MyFunction<String, String> fn) {
      System.out.println(fn.apply("Hello"));
    }

    public static void main(String[] args) {
      run(prefix -> prefix + ", world");
    }
  }
}
