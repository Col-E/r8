// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.UnverifiableCfCodeDiagnostic;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class UndefinedLambdaInterfaceRegress232379893 extends TestBase {

  static final String EXPECTED = StringUtils.lines("Hello, world");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  public UndefinedLambdaInterfaceRegress232379893(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .addProgramClassesAndInnerClasses(UserOfUndefinedInterface.class)
        .addKeepMainRule(TestClass.class)
        .addDontWarn(UndefinedInterface.class)
        .addDontShrink()
        .allowDiagnosticWarningMessages(parameters.isDexRuntime())
        .setMinApi(parameters)
        .compileWithExpectedDiagnostics(
            diagnostics -> {
              if (parameters.isDexRuntime()) {
                diagnostics.assertWarningsMatch(
                    allOf(
                        diagnosticType(UnverifiableCfCodeDiagnostic.class),
                        diagnosticMessage(
                            containsString(
                                "Unverifiable code in `void "
                                    + TestClass.class.getTypeName()
                                    + ".main(java.lang.String[])`"))));
              }
            })
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  interface UndefinedInterface {
    void foo();
  }

  static class UserOfUndefinedInterface {
    public static void bar(UndefinedInterface i) {
      throw new RuntimeException("unused method");
    }
  }

  static class TestClass {
    public static void main(String[] args) {
      if (System.nanoTime() < 0) {
        UserOfUndefinedInterface.bar(() -> System.out.println("unused lambda"));
      } else {
        System.out.println("Hello, world");
      }
    }
  }
}
