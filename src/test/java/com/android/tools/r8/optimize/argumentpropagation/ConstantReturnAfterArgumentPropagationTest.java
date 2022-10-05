// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;

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
public class ConstantReturnAfterArgumentPropagationTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection parameters() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        // TODO(b/173398086): uniqueMethodWithName() does not work with argument removal.
        .addDontObfuscate()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(
            inspector -> {
              ClassSubject mainClassSubject = inspector.clazz(Main.class);
              assertThat(mainClassSubject, isPresent());

              MethodSubject identityMethodSubject =
                  mainClassSubject.uniqueMethodWithOriginalName("identity");
              assertThat(identityMethodSubject, isPresent());
              assertTrue(identityMethodSubject.getProgramMethod().getParameters().isEmpty());
              assertTrue(identityMethodSubject.getProgramMethod().getReturnType().isVoidType());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("identity(42) = 42");
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println(identity("42"));
    }

    @NeverInline
    static Object identity(Object constant) {
      System.out.print("identity(" + constant + ") = ");
      return constant;
    }
  }
}
