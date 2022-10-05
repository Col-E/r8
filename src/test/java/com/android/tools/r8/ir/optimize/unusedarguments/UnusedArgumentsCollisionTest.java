// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.unusedarguments;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoMethodStaticizing;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class UnusedArgumentsCollisionTest extends TestBase {

  private final boolean minification;
  private final TestParameters parameters;

  @Parameters(name = "{1}, minification: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public UnusedArgumentsCollisionTest(boolean minification, TestParameters parameters) {
    this.minification = minification;
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    String expectedOutput = StringUtils.lines("Hello world", "Hello world");

    if (parameters.isCfRuntime() && !minification) {
      testForJvm()
          .addTestClasspath()
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutput(expectedOutput);
    }

    testForR8(parameters.getBackend())
        .addInnerClasses(UnusedArgumentsCollisionTest.class)
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoMethodStaticizingAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .minification(minification)
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::verifyUnusedArgumentsRemovedAndNoCollisions)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(expectedOutput);
  }

  private void verifyUnusedArgumentsRemovedAndNoCollisions(CodeInspector inspector) {
    ClassSubject aClassSubject = inspector.clazz(A.class);
    assertThat(aClassSubject, isPresent());

    MethodSubject methodA1Subject = aClassSubject.uniqueMethodWithOriginalName("method1");
    assertThat(methodA1Subject, isPresent());

    MethodSubject methodA2Subject = aClassSubject.uniqueMethodWithOriginalName("method2");
    assertThat(methodA2Subject, isPresent());

    ClassSubject bClassSubject = inspector.clazz(B.class);
    assertThat(bClassSubject, isPresent());

    // Verify that the unused argument has been removed from B.method1().
    // TODO(b/120764902): Name mapping not working in presence of unused argument removal.
    MethodSubject methodB1Subject =
        bClassSubject.allMethods().stream().filter(FoundMethodSubject::isStatic).findFirst().get();
    assertThat(methodB1Subject, isPresent());
    assertEquals(0, methodB1Subject.getMethod().getParameters().size());

    // Verify that the static method B.method1() does not collide with a method in A.
    assertNotEquals(methodB1Subject.getFinalName(), methodA1Subject.getFinalName());
    assertNotEquals(methodB1Subject.getFinalName(), methodA2Subject.getFinalName());

    // Verify that the unused argument has been removed from B.method2().
    // TODO(b/120764902): Name mapping not working in presence of unused argument removal.
    MethodSubject methodB2Subject =
        bClassSubject.allMethods().stream().filter(FoundMethodSubject::isVirtual).findFirst().get();
    assertThat(methodB2Subject, isPresent());
    assertEquals(0, methodB2Subject.getMethod().getParameters().size());

    // Verify that the virtual method B.method2() does not collide with a method in A.
    assertNotEquals(methodB2Subject.getFinalName(), methodA1Subject.getFinalName());
    assertNotEquals(methodB2Subject.getFinalName(), methodA2Subject.getFinalName());
  }

  static class TestClass {

    public static void main(String[] args) {
      B obj = new B();
      obj.method1();
      obj.method1(new Object());
      obj.method2();
      obj.method2(new Object());
    }
  }

  @NoVerticalClassMerging
  static class A {

    @NeverInline
    @NoMethodStaticizing
    public void method1() {
      System.out.print("Hello");
    }

    @NeverInline
    public static void method2() {
      System.out.print("Hello");
    }
  }

  @NeverClassInline
  static class B extends A {

    @NeverInline
    public static void method1(Object unused) {
      System.out.println(" world");
    }

    @NeverInline
    @NoMethodStaticizing
    public void method2(Object unused) {
      System.out.println(" world");
    }
  }
}
