// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.cf.code.CfTryCatch;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexCode.TryHandler;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SubsumedCatchHandlerTest extends TestBase {

  private static final String EXPECTED = "In bar() -> 0";

  static class TestClass {

    public static void main(String[] args) {
      int exitCode = 0;
      try {
        exitCode = foo();
      } catch (RuntimeException e) {
        // This catch handler will be subsumed by the catch handler in foo() after inlining.
        handleCaughtRuntimeException();
      }
      System.out.print(" -> " + exitCode);
    }

    private static int foo() {
      try {
        bar();
      } catch (Exception e) {
        // No statements with side-effects here; otherwise this block will be guarded by the
        // RuntimeException catch handler from main().
        return 1;
      }
      return 0;
    }

    @NeverInline
    private static void bar() {
      System.out.print("In bar()");
    }

    @NeverInline
    private static void handleCaughtRuntimeException() {
      System.out.print("In handleCaughtRuntimeException()");
    }
  }

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Parameter(0)
  public TestParameters parameters;

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addTestClasspath()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    CodeInspector inspector =
        testForR8(parameters.getBackend())
            .addInnerClasses(SubsumedCatchHandlerTest.class)
            .addKeepMainRule(TestClass.class)
            .enableInliningAnnotations()
            .setMinApi(parameters)
            .run(parameters.getRuntime(), TestClass.class)
            .assertSuccessWithOutput(EXPECTED)
            .inspector();

    ClassSubject classSubject = inspector.clazz(TestClass.class);
    assertThat(classSubject, isPresent());
    assertThat(classSubject.method("void", "handleCaughtRuntimeException"), not(isPresent()));

    MethodSubject mainMethodSubject = classSubject.mainMethod();
    Code code = mainMethodSubject.getMethod().getCode();
    if (code.isDexCode()) {
      DexCode dexCode = code.asDexCode();
      assertEquals(1, dexCode.handlers.length);

      TryHandler handler = dexCode.handlers[0];
      assertEquals(1, handler.pairs.length);

      DexType guard = handler.pairs[0].getType();
      assertEquals("java.lang.Exception", guard.toSourceString());
    } else {
      assert code.isCfCode();
      CfCode cfCode = code.asCfCode();
      assertEquals(1, cfCode.getTryCatchRanges().size());

      CfTryCatch handler = cfCode.getTryCatchRanges().get(0);
      assertEquals(1, handler.guards.size());

      DexType guard = handler.guards.get(0);
      assertEquals("java.lang.Exception", guard.toSourceString());
    }

    assertThat(classSubject.uniqueMethodWithOriginalName("foo"), isAbsent());
  }
}
