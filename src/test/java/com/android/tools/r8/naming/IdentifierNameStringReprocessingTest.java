// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverPropagateValue;
import com.android.tools.r8.ReprocessClassInitializer;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IdentifierNameStringReprocessingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepClassRulesWithAllowObfuscation(A.class)
        .addKeepRules(
            "-identifiernamestring class " + Main.class.getTypeName() + " {",
            "  static java.lang.String f;",
            "}")
        .enableMemberValuePropagationAnnotations()
        .enableReprocessClassInitializerAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .apply(
            runResult -> {
              ClassSubject aClassSubject = runResult.inspector().clazz(A.class);
              assertThat(aClassSubject, isPresentAndRenamed());
              runResult.assertSuccessWithOutputLines(aClassSubject.getFinalName());
            });
  }

  @ReprocessClassInitializer
  static class Main {

    @NeverPropagateValue static String f;

    static {
      // Prevent class initializer defaults optimization.
      System.out.print("");
      f = "com.android.tools.r8.naming.IdentifierNameStringReprocessingTest$A";
    }

    public static void main(String[] args) {
      System.out.println(f);
    }
  }

  static class A {}
}
