// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.typechecks;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class InstanceOfMethodSpecializationTest extends TestBase {

  private static final List<String> EXPECTED =
      ImmutableList.of(
          "true", "false", "false", "true", "false", "true", "true", "false", "false", "true",
          "true", "false", "true", "false", "true");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public InstanceOfMethodSpecializationTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testJvm() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    testForJvm()
        .addTestClasspath()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(InstanceOfMethodSpecializationTest.class)
        .addKeepMainRule(TestClass.class)
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject aClassSubject = inspector.clazz(A.class);
    assertThat(aClassSubject, isPresent());
    assertThat(aClassSubject.uniqueMethodWithName("isA"), not(isPresent()));
    assertThat(aClassSubject.uniqueMethodWithName("isB"), not(isPresent()));
    assertThat(aClassSubject.uniqueMethodWithName("isC"), not(isPresent()));
    assertThat(aClassSubject.uniqueMethodWithName("isSuper"), isPresent());
    assertThat(aClassSubject.uniqueMethodWithName("isSub"), isPresent());

    ClassSubject bClassSubject = inspector.clazz(B.class);
    assertThat(bClassSubject, isPresent());
    assertThat(bClassSubject.uniqueMethodWithName("isA"), not(isPresent()));
    assertThat(bClassSubject.uniqueMethodWithName("isB"), not(isPresent()));
    assertThat(bClassSubject.uniqueMethodWithName("isC"), not(isPresent()));
    assertThat(bClassSubject.uniqueMethodWithName("isSuper"), not(isPresent()));
    assertThat(bClassSubject.uniqueMethodWithName("isSub"), not(isPresent()));

    ClassSubject cClassSubject = inspector.clazz(C.class);
    assertThat(cClassSubject, isPresent());
    assertThat(cClassSubject.uniqueMethodWithName("isA"), not(isPresent()));
    assertThat(cClassSubject.uniqueMethodWithName("isB"), not(isPresent()));
    assertThat(cClassSubject.uniqueMethodWithName("isC"), not(isPresent()));
    assertThat(cClassSubject.uniqueMethodWithName("isSuper"), isPresent());
    assertThat(cClassSubject.uniqueMethodWithName("isSub"), isPresent());
  }

  public static class TestClass {

    public static void main(String[] args) {
      A a = System.currentTimeMillis() > 0 ? new A() : new B();
      A b = System.currentTimeMillis() > 0 ? new B() : new A();
      A c = System.currentTimeMillis() > 0 ? new C() : new A();
      System.out.println(a.isA());
      System.out.println(a.isB());
      System.out.println(a.isC());
      System.out.println(a.isSuper());
      System.out.println(a.isSub());
      System.out.println(b.isA());
      System.out.println(b.isB());
      System.out.println(b.isC());
      System.out.println(b.isSuper());
      System.out.println(b.isSub());
      System.out.println(c.isA());
      System.out.println(c.isB());
      System.out.println(c.isC());
      System.out.println(c.isSuper());
      System.out.println(c.isSub());
    }
  }

  public static class A {

    boolean isA() {
      return true;
    }

    boolean isB() {
      return false;
    }

    boolean isC() {
      return false;
    }

    boolean isSuper() {
      return true;
    }

    boolean isSub() {
      return false;
    }
  }

  public static class B extends A {

    @Override
    boolean isA() {
      return true;
    }

    @Override
    boolean isB() {
      return true;
    }

    @Override
    boolean isSuper() {
      return false;
    }

    @Override
    boolean isSub() {
      return true;
    }
  }

  @NoHorizontalClassMerging
  public static class C extends A {

    @Override
    boolean isA() {
      return true;
    }

    @Override
    boolean isC() {
      return true;
    }

    @Override
    boolean isSuper() {
      return false;
    }

    @Override
    boolean isSub() {
      return true;
    }
  }
}
