// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.outliner;

import static com.android.tools.r8.ir.desugar.itf.InterfaceDesugaringForTesting.getCompanionClassNameSuffix;
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
import com.android.tools.r8.utils.codeinspector.HorizontallyMergedClassesInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class OutlineFromStaticInterfaceMethodTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public OutlineFromStaticInterfaceMethodTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .addOptionsModification(
            options -> {
              options.outline.threshold = 2;
              options.outline.minSize = 2;
            })
        .addHorizontallyMergedClassesInspector(
            HorizontallyMergedClassesInspector::assertNoClassesMerged)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .noHorizontalClassMergingOfSynthetics()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!", "Hello world!");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject interfaceSubject =
        parameters.isCfRuntime()
                || parameters
                    .getApiLevel()
                    .isGreaterThanOrEqualTo(apiLevelWithStaticInterfaceMethodsSupport())
            ? inspector.clazz(I.class)
            : inspector.clazz(I.class.getTypeName() + getCompanionClassNameSuffix());
    assertThat(interfaceSubject, isPresent());

    MethodSubject greetMethodSubject = interfaceSubject.uniqueMethodWithOriginalName("greet");
    assertThat(greetMethodSubject, isPresent());
    assertEquals(
        1,
        greetMethodSubject.streamInstructions().filter(InstructionSubject::isInvokeStatic).count());
  }

  static class TestClass {

    public static void main(String... args) {
      greet();
      I.greet();
    }

    @NeverInline
    static void greet() {
      Greeter.hello();
      Greeter.world();
    }
  }

  interface I {

    @NeverInline
    static void greet() {
      Greeter.hello();
      Greeter.world();
    }
  }

  @NeverClassInline
  public static class Greeter {

    @NeverInline
    public static void hello() {
      System.out.print("Hello");
    }

    @NeverInline
    public static void world() {
      System.out.println(" world!");
    }
  }
}
