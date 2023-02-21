// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.switches;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class SwitchMapInvalidInitTest extends TestBase {
  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public SwitchMapInvalidInitTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testInvalidInitSwitchMap() throws Exception {
    testForR8(parameters.getBackend())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .addInnerClasses(SwitchMapInvalidInitTest.class)
        .setMinApi(parameters)
        .compile()
        .inspect(this::assertNoSwitchMap)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("init", "init", "init", "a", "b", "a", "b");
  }

  private void assertNoSwitchMap(CodeInspector codeInspector) {
    // Main and the 2 enum classes.
    assertEquals(3, codeInspector.allClasses().size());
    assertThat(codeInspector.clazz(Main.class), isPresent());
    assertThat(codeInspector.clazz(MyEnum1.class), isPresent());
    assertThat(codeInspector.clazz(MyEnum2.class), isPresent());
  }

  @NeverClassInline
  enum MyEnum1 {
    // The System call cannot be traced, therefore enum field can be read before being set.
    A(System.currentTimeMillis()),
    B(0),
    C(1);
    private final long time;

    MyEnum1(long time) {
      this.time = time;
    }
  }

  @NeverClassInline
  enum MyEnum2 {
    A,
    B,
    C;

    MyEnum2() {
      // The System call cannot be traced, therefore any enum field can be read before being set.
      System.out.println("init");
    }
  }

  public static class Main {
    public static void main(String[] args) {
      switch2(MyEnum2.A);
      switch2(MyEnum2.B);
      switch2(MyEnum2.C);
      switch1(MyEnum1.A);
      switch1(MyEnum1.B);
      switch1(MyEnum1.C);
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
