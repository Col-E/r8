// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.outliner;

import static com.android.tools.r8.references.Reference.methodFromMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.HorizontallyMergedClassesInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

// Regression test for the issue causing extra bookkeeping in outlining of interface methods.
// See clean-up issue b/167345026.
@RunWith(Parameterized.class)
public class OutlineFromDefaultInterfaceMethodTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public OutlineFromDefaultInterfaceMethodTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .addKeepMethodRules(methodFromMethod(TestClass.class.getDeclaredMethod("getI")))
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
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!", "Hello world!");
  }

  private void inspect(CodeInspector inspector) {
    if (parameters.canUseDefaultAndStaticInterfaceMethods()) {
      ClassSubject interfaceSubject = inspector.clazz(I.class);
      MethodSubject greetMethodSubject = interfaceSubject.uniqueMethodWithOriginalName("greet");
      assertThat(interfaceSubject, isPresent());
      assertThat(greetMethodSubject, isPresent());
      assertEquals(
          1,
          greetMethodSubject
              .streamInstructions()
              .filter(InstructionSubject::isInvokeStatic)
              .count());
    } else {
      // The companion class method is inlined into main.
      assertThat(
          inspector.clazz(SyntheticItemsTestUtils.syntheticCompanionClass(I.class)), isAbsent());
    }
  }

  static class TestClass {

    static I getI() {
      return new I() {};
    }

    public static void main(String... args) {
      greet();
      getI().greet();
    }

    @NeverInline
    static void greet() {
      Greeter.hello();
      Greeter.world();
    }
  }

  interface I {

    @NeverInline
    default void greet() {
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
