// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner.interfacemethods;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InlineDefaultInterfaceMethodTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public InlineDefaultInterfaceMethodTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    String expectedOutput = StringUtils.lines("Hello world!");

    CodeInspector inspector =
        testForR8(parameters.getBackend())
            .addInnerClasses(InlineDefaultInterfaceMethodTest.class)
            .addKeepMainRule(TestClass.class)
            .setMinApi(parameters)
            .enableNeverClassInliningAnnotations()
            .enableNoVerticalClassMergingAnnotations()
            .addDontObfuscate()
            .run(parameters.getRuntime(), TestClass.class)
            .assertSuccessWithOutput(expectedOutput)
            .inspector();

    // After inlining, only one class remains, namely TestClass.
    assertEquals(1, inspector.allClasses().size());
  }

  static class TestClass {

    public static void main(String[] args) {
      new C().method();
    }
  }

  @NoVerticalClassMerging
  interface I {

    default void hello() {
      System.out.print("Hello");
    }

    default void space() {
      System.out.print(" ");
    }

    default void world() {
      System.out.println("world!");
    }
  }

  @NeverClassInline
  static class C implements I {

    public void method() {
      // invoke-virtual
      hello();
      // invoke-interface
      I self = this;
      self.space();
      // invoke-super
      I.super.world();
    }
  }
}
