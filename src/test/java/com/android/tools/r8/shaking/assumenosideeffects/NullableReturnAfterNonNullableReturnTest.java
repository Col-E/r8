// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.assumenosideeffects;

import com.android.tools.r8.AssumeNoSideEffects;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.ReprocessMethod;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class NullableReturnAfterNonNullableReturnTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public NullableReturnAfterNonNullableReturnTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .addKeepMainRule(TestClass.class)
        .enableAssumeNoSideEffectsAnnotations()
        .enableInliningAnnotations()
        .enableReprocessMethodAnnotations()
        .setMinApi(parameters)
        .compile();
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(test());
    }

    @NeverInline
    @ReprocessMethod
    static Object test() {
      String s = System.currentTimeMillis() > 0 ? "Hello world!" : null;
      checkNotNull(s);
      return s;
    }

    @AssumeNoSideEffects
    static void checkNotNull(Object o) {
      if (o == null) {
        throw new RuntimeException();
      }
    }
  }
}
