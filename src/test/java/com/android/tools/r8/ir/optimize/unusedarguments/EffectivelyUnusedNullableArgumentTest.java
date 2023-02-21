// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.unusedarguments;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethodWithName;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isStatic;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EffectivelyUnusedNullableArgumentTest extends TestBase {

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
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        // TODO(b/173398086): uniqueMethodWithName() does not work with argument changes.
        .addDontObfuscate()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject aClassSubject = inspector.clazz(A.class);

              MethodSubject fooMethodSubject = aClassSubject.uniqueMethodWithOriginalName("foo");
              assertThat(fooMethodSubject, isPresent());
              assertEquals(1, fooMethodSubject.getProgramMethod().getParameters().size());
              assertEquals(
                  aClassSubject.getDexProgramClass().getType(),
                  fooMethodSubject.getProgramMethod().getParameter(0));
              assertThat(fooMethodSubject, invokesMethodWithName("getClass"));

              MethodSubject barMethodSubject = aClassSubject.uniqueMethodWithOriginalName("bar");
              assertThat(barMethodSubject, isStatic());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(NullPointerException.class);
  }

  static class Main {

    public static void main(String[] args) {
      A a = System.currentTimeMillis() > 0 ? null : new A();
      A.foo(a);
    }
  }

  @NeverClassInline
  static class A {

    // This parameter cannot be removed, since the program needs to fail at the call to A.bar() when
    // A.foo(null) is called.
    @NeverInline
    static void foo(A a) {
      a.bar();
    }

    @NeverInline
    void bar() {
      System.out.println("A.bar()");
    }
  }
}
