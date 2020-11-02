// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.membervaluepropagation;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MemberValuePropagationOfNullValueTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  public MemberValuePropagationOfNullValueTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, A.class)
        .setMinApi(parameters.getApiLevel())
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .addKeepMainRule(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(NullPointerException.class);
  }

  @NeverClassInline
  public static class A {

    public int field = 1;
  }

  public static class Main {

    public static void main(String[] args) {
      new Main().getFieldOfA();
    }

    @NeverInline
    public void getFieldOfA() {
      A a = System.currentTimeMillis() > 0 ? getA() : null;
      // R8 identifies that we always return null from getA() but the phi is typed at the concrete
      // type: phi(v1(0), v2(0)) : @Nullable A.
      System.out.println(a.field);
    }

    @NeverInline
    public A getA() {
      return null;
    }
  }
}
