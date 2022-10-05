// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner.conditionalsimpleinlining;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.AlwaysInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeMatchers;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NopInliningConstraintTest extends TestBase {

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableAlwaysInliningAnnotations()
        .enableInliningAnnotations()
        // TODO(b/173398086): uniqueMethodWithName() does not work with argument removal.
        .addDontObfuscate()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(
            inspector -> {
              ClassSubject mainClassSubject = inspector.clazz(Main.class);
              assertThat(mainClassSubject, isPresent());

              // Method doStuff() is inlined into main().
              assertThat(mainClassSubject.uniqueMethodWithOriginalName("doStuff"), isAbsent());

              // Method checkNotNull() is not inlined.
              MethodSubject checkNotNullMethodSubject =
                  mainClassSubject.uniqueMethodWithOriginalName("checkNotNull");
              assertThat(checkNotNullMethodSubject, isPresent());

              // There is a single call to checkNotNull() in main(), as checkNotNull(newObject())
              // is dead code eliminated.
              assertEquals(
                  1,
                  mainClassSubject
                      .mainMethod()
                      .streamInstructions()
                      .filter(CodeMatchers.isInvokeWithTarget(checkNotNullMethodSubject))
                      .count());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(
            "Caught NPE: Parameter specified as non-null is null: method "
                + Main.class.getTypeName()
                + ".main, parameter o");
  }

  static class Main {
    public static void main(String[] args) {
      doStuff(new Object());
      try {
        doStuff(null);
      } catch (NullPointerException e) {
        System.out.println("Caught NPE: " + e.getMessage());
      }
    }

    @AlwaysInline
    static void doStuff(Object o) {
      checkNotNull(o, "o");
    }

    @NeverInline
    static void checkNotNull(Object o, String parameterName) {
      if (o != null) {
        return;
      }
      StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
      int callerStackTraceElementIndex =
          stackTrace[0].getMethodName().equals("getThreadStackTrace") ? 3 : 2;
      StackTraceElement callerStackTraceElement = stackTrace[callerStackTraceElementIndex];
      throw new NullPointerException(
          "Parameter specified as non-null is null: method "
              + callerStackTraceElement.getClassName()
              + "."
              + callerStackTraceElement.getMethodName()
              + ", parameter "
              + parameterName);
    }
  }
}
