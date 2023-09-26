// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner.whyareyounotinlining;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;

import com.android.tools.r8.DiagnosticsMatcher;
import com.android.tools.r8.NeverReprocessMethod;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ir.optimize.inliner.WhyAreYouNotInliningDiagnostic;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class WhyAreYouNotInliningInvokeWithUnknownTargetTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultRuntimes().withApiLevel(AndroidApiLevel.B).build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .addKeepRules("-whyareyounotinlining class " + A.class.getTypeName() + " { void m(); }")
        .enableExperimentalWhyAreYouNotInlining()
        .enableNeverReprocessMethodAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters)
        .allowDiagnosticInfoMessages()
        .compile()
        .inspectDiagnosticMessages(
            testDiagnosticMessages ->
                testDiagnosticMessages.assertInfosMatch(
                    allOf(
                        DiagnosticsMatcher.diagnosticType(WhyAreYouNotInliningDiagnostic.class),
                        DiagnosticsMatcher.diagnosticMessage(
                            is(
                                "Method `void "
                                    + A.class.getTypeName()
                                    + ".m()` was not inlined into `void "
                                    + TestClass.class.getTypeName()
                                    + ".main(java.lang.String[])`: "
                                    + "could not find a single target.")))));
  }

  static class TestClass {

    @NeverReprocessMethod
    public static void main(String[] args) {
      (System.currentTimeMillis() >= 0 ? new A() : new B()).m();
    }
  }

  interface I {

    void m();
  }

  static class A implements I {

    @Override
    public void m() {
      System.out.println("A.m()");
    }
  }

  @NoHorizontalClassMerging
  static class B implements I {

    @Override
    public void m() {
      System.out.println("B.m()");
    }
  }
}
