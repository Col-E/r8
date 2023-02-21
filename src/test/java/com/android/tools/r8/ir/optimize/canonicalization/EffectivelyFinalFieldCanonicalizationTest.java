// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.canonicalization;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class EffectivelyFinalFieldCanonicalizationTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public EffectivelyFinalFieldCanonicalizationTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(EffectivelyFinalFieldCanonicalizationTest.class)
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("[R8] Foo", "[R8] Bar");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject testClassSubject = inspector.clazz(TestClass.class);
    assertThat(testClassSubject, isPresent());

    MethodSubject mainMethodSubject = testClassSubject.mainMethod();
    assertThat(mainMethodSubject, isPresent());
    assertEquals(
        1, mainMethodSubject.streamInstructions().filter(InstructionSubject::isStaticGet).count());
  }

  static class TestClass {

    public static void main(String[] args) {
      Printer.INSTANCE.print("Foo");
      Printer.INSTANCE.print("Bar");
    }
  }

  @NeverClassInline
  static class Printer {

    static Printer INSTANCE = new Printer("[R8]");

    private String tag;

    Printer(String tag) {
      this.tag = tag;
    }

    @NeverInline
    void print(String message) {
      System.out.println(tag + " " + message);
    }
  }
}
