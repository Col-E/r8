// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoMethodStaticizing;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.UnverifiableCfCodeDiagnostic;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MissingClassJoinsToObjectTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("A::foo");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  public MissingClassJoinsToObjectTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private List<Path> getRuntimeClasspath() throws Exception {
    return buildOnDexRuntime(parameters, ToolHelper.getClassFileForTestClass(B.class));
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(TestClass.class, A.class, B.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    R8TestRunResult result =
        testForR8(parameters.getBackend())
            .enableInliningAnnotations()
            .addProgramClasses(TestClass.class, A.class)
            .addKeepMainRule(TestClass.class)
            .addDontWarn(B.class)
            .allowDiagnosticWarningMessages()
            .enableNoMethodStaticizingAnnotations()
            .setMinApi(parameters)
            .compileWithExpectedDiagnostics(
                diagnostics ->
                    diagnostics.assertWarningsMatch(
                        allOf(
                            diagnosticType(UnverifiableCfCodeDiagnostic.class),
                            diagnosticMessage(
                                containsString(
                                    "Unverifiable code in `void "
                                        + TestClass.class.getTypeName()
                                        + ".main(java.lang.String[])`")))))
            .addRunClasspathFiles(getRuntimeClasspath())
            .run(parameters.getRuntime(), TestClass.class);
    if (parameters.isCfRuntime()) {
      // TODO(b/154792347): The analysis of types in the presence of undefined is incomplete.
      result.assertFailureWithErrorThatThrows(VerifyError.class);
    } else {
      result.assertSuccessWithOutput(EXPECTED);
    }
  }

  static class A {
    @NeverInline
    @NoMethodStaticizing
    public void foo() {
      System.out.println("A::foo");
    }
  }

  // Missing at compile time.
  static class B extends A {
    // Intentionally empty.
  }

  static class TestClass {

    public static void main(String[] args) {
      // Due to the missing class B, the join is assigned Object.
      A join = args.length == 0 ? new A() : new B();
      // The call to Object::foo fails.
      join.foo();
    }
  }
}
