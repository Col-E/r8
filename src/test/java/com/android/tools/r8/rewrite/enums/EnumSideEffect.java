// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.rewrite.enums;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class EnumSideEffect extends TestBase {
  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public EnumSideEffect(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testD8AndJava() throws Exception {
    testForRuntime(parameters.getRuntime(), parameters.getApiLevel())
        .addInnerClasses(EnumSideEffect.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("clinit", "init", "init", "a", "b", "a", "b");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .addInnerClasses(EnumSideEffect.class)
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("clinit", "a", "b", "init", "init", "a", "b");
    // TODO(b/173580704): should be the following result as in D8:
    // .assertSuccessWithOutputLines("clinit", "init", "init", "a", "b", "a", "b");
  }

  @NeverClassInline
  enum MyEnum1 {
    A,
    B;

    static {
      System.out.println("clinit");
    }
  }

  @NeverClassInline
  enum MyEnum2 {
    A,
    B;

    MyEnum2() {
      System.out.println("init");
    }
  }

  public static class Main {
    public static void main(String[] args) {
      switch1(MyEnum1.A);
      switch1(MyEnum1.B);
      switch2(MyEnum2.A);
      switch2(MyEnum2.B);
    }

    @NeverInline
    private static void switch1(MyEnum1 e) {
      switch (e) {
        case A:
          System.out.println("a");
          break;
        case B:
          System.out.println("b");
          break;
      }
    }

    @NeverInline
    private static void switch2(MyEnum2 e) {
      switch (e) {
        case A:
          System.out.println("a");
          break;
        case B:
          System.out.println("b");
          break;
      }
    }
  }
}
