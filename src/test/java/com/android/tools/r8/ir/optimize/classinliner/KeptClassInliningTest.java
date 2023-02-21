// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.classinliner;

import static com.android.tools.r8.references.Reference.methodFromMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KeptClassInliningTest extends TestBase {

  public static class KeptClass {

    // Annotate with never-inline to avoid the method inliner from eliminating the call, which in
    // turn allows removing the instantiation.
    @NeverInline
    public void used() {
      System.out.println("used()");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      new KeptClass().used();
    }
  }

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public final TestParameters parameters;

  public KeptClassInliningTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    CodeInspector inspector =
        testForR8(parameters.getBackend())
            .enableInliningAnnotations()
            .addProgramClasses(KeptClass.class, Main.class)
            .addKeepMainRule(Main.class)
            .addKeepClassRules(KeptClass.class)
            .setMinApi(parameters)
            .run(parameters.getRuntime(), Main.class)
            .assertSuccessWithOutputLines("used()")
            .inspector();
    assertThat(inspector.clazz(KeptClass.class), isPresent());
    MethodSubject main =
        inspector.method(methodFromMethod(Main.class.getMethod("main", String[].class)));
    // Check the instantiation of the class remains in 'main'.
    assertTrue(
        main.streamInstructions()
            .anyMatch(i -> i.isInvoke() && i.getMethod().name.toString().equals("<init>")));
  }
}
