// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.callsites;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KeptMethodTest extends TestBase {
  private static final Class<?> MAIN = Main.class;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Parameter(0)
  public TestParameters parameters;

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(KeptMethodTest.class)
        .addKeepMainRule(MAIN)
        .addKeepClassAndMembersRules(A.class)
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .addOptionsModification(
            o ->
                o.testing.callSiteOptimizationInfoInspector = this::callSiteOptimizationInfoInspect)
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutputLines("non-null", "non-null")
        .inspect(this::inspect);
  }

  private void callSiteOptimizationInfoInspect(ProgramMethod method) {
    assert false : "Unexpected revisit: " + method.toSourceString();
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject a = inspector.clazz(A.class);
    assertThat(a, isPresent());

    MethodSubject m = a.uniqueMethodWithOriginalName("m");
    assertThat(m, isPresent());
    // Should not optimize branches since the method is kept, accessed via reflection.
    assertTrue(m.streamInstructions().anyMatch(InstructionSubject::isIf));
  }

  @NeverClassInline
  static class A {
    @NeverInline
    void m(Object arg) {
      // Technically same as String#valueOf.
      if (arg != null) {
        System.out.println(arg.toString());
      } else {
        System.out.println("null");
      }
    }
  }

  static class Main {
    public static void main(String... args) throws Exception {
      A obj = new A();
      obj.m("non-null");
      for (Method m : obj.getClass().getDeclaredMethods()) {
        // Again, calls A#m with non-null string.
        m.invoke(obj, "non-null");
      }
    }
  }
}
