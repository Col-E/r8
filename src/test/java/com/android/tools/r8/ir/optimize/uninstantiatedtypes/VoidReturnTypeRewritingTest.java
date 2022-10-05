// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.uninstantiatedtypes;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoMethodStaticizing;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class VoidReturnTypeRewritingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    String expected =
        StringUtils.lines(
            "Factory.createStatic() -> null",
            "Factory.createVirtual() -> null",
            "SubFactory.createVirtual() -> null",
            "SubSubFactory.createVirtual() -> null");

    if (parameters.isCfRuntime()) {
      testForJvm()
          .addTestClasspath()
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutput(expected);
    }

    CodeInspector inspector =
        testForR8(parameters.getBackend())
            .addInnerClasses(VoidReturnTypeRewritingTest.class)
            .addKeepMainRule(TestClass.class)
            .enableInliningAnnotations()
            .enableNoMethodStaticizingAnnotations()
            .enableNoVerticalClassMergingAnnotations()
            .enableNoHorizontalClassMergingAnnotations()
            .addOptionsModification(options -> options.enableClassInlining = false)
            .addDontObfuscate()
            .setMinApi(parameters.getApiLevel())
            .run(parameters.getRuntime(), TestClass.class)
            .assertSuccessWithOutput(expected)
            .inspector();

    ClassSubject factoryClassSubject = inspector.clazz(Factory.class);
    MethodSubject createStaticMethodSubject =
        factoryClassSubject.uniqueMethodWithOriginalName("createStatic");
    assertThat(createStaticMethodSubject, isPresent());
    assertTrue(createStaticMethodSubject.getMethod().getReturnType().isVoidType());
    MethodSubject createVirtualMethodSubject =
        factoryClassSubject.uniqueMethodWithOriginalName("createVirtual");
    assertThat(createVirtualMethodSubject, isPresent());
    assertTrue(createVirtualMethodSubject.getMethod().getReturnType().isVoidType());

    createVirtualMethodSubject =
        inspector.clazz(SubFactory.class).uniqueMethodWithOriginalName("createVirtual");
    assertThat(createVirtualMethodSubject, isPresent());
    assertTrue(createVirtualMethodSubject.getMethod().getReturnType().isVoidType());

    ClassSubject subSubFactoryClassSubject = inspector.clazz(SubSubFactory.class);
    assertThat(subSubFactoryClassSubject.method("void", "createVirtual"), isPresent());
    assertThat(subSubFactoryClassSubject.method("void", "createVirtual$1"), isPresent());
    assertThat(inspector.clazz(Uninstantiated.class), isAbsent());
    assertThat(inspector.clazz(SubUninstantiated.class), isAbsent());
  }

  static class TestClass {

    public static void main(String[] args) {
      Uninstantiated obj1 = Factory.createStatic();
      System.out.println(" -> " + obj1);

      Uninstantiated obj2 = new Factory().createVirtual();
      System.out.println(" -> " + obj2);

      Uninstantiated obj3 = new SubFactory().createVirtual();
      System.out.println(" -> " + obj3);

      Uninstantiated obj4 = new SubSubFactory().createVirtual();
      System.out.println(" -> " + obj4);
    }
  }

  @NoHorizontalClassMerging
  @NoVerticalClassMerging
  static class Uninstantiated {}

  @NoVerticalClassMerging
  static class SubUninstantiated extends Uninstantiated {}

  @NoVerticalClassMerging
  static class Factory {

    @NeverInline
    public static Uninstantiated createStatic() {
      System.out.print("Factory.createStatic()");
      return null;
    }

    @NeverInline
    @NoMethodStaticizing
    public Uninstantiated createVirtual() {
      System.out.print("Factory.createVirtual()");
      return null;
    }
  }

  @NoVerticalClassMerging
  static class SubFactory extends Factory {

    @Override
    @NeverInline
    @NoMethodStaticizing
    public Uninstantiated createVirtual() {
      System.out.print("SubFactory.createVirtual()");
      return null;
    }
  }

  @NoVerticalClassMerging
  static class SubSubFactory extends SubFactory {

    @Override
    @NeverInline
    @NoMethodStaticizing
    public SubUninstantiated createVirtual() {
      System.out.print("SubSubFactory.createVirtual()");
      return null;
    }
  }
}
