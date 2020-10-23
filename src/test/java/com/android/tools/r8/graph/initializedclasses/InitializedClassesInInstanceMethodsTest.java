// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.initializedclasses;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InitializedClassesInInstanceMethodsTest extends TestBase {

  private final boolean enableInitializedClassesInInstanceMethodsAnalysis;
  private final TestParameters parameters;

  @Parameters(name = "{1}, enabled: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public InitializedClassesInInstanceMethodsTest(
      boolean enableInitializedClassesInInstanceMethodsAnalysis, TestParameters parameters) {
    this.enableInitializedClassesInInstanceMethodsAnalysis =
        enableInitializedClassesInInstanceMethodsAnalysis;
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(InitializedClassesInInstanceMethodsTest.class)
        .addKeepMainRule(TestClass.class)
        .addOptionsModification(
            options -> {
              options.enableInitializedClassesInInstanceMethodsAnalysis =
                  enableInitializedClassesInInstanceMethodsAnalysis;
            })
        .allowAccessModification()
        .enableNeverClassInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .enableInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::verifyOutput)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private void verifyOutput(CodeInspector inspector) {
    ClassSubject outerClassSubject = inspector.clazz(Outer.class);
    assertThat(outerClassSubject, isPresent());

    // In any case, Outer.hello(), Outer.world(), and Outer.exclamationMark() should be inlined into
    // the accessibility bridges.
    assertThat(outerClassSubject.uniqueMethodWithName("hello"), not(isPresent()));
    assertThat(outerClassSubject.uniqueMethodWithName("world"), not(isPresent()));
    assertThat(outerClassSubject.uniqueMethodWithName("exclamationMark"), not(isPresent()));

    int numberOfExpectedAccessibilityBridges = 0;
    assertEquals(
        numberOfExpectedAccessibilityBridges,
        outerClassSubject
            .allMethods(method -> method.getOriginalName().contains("access$"))
            .size());
    assertEquals(
        !enableInitializedClassesInInstanceMethodsAnalysis,
        outerClassSubject.uniqueFieldWithName("$r8$clinit").isPresent());

    ClassSubject aClassSubject = inspector.clazz(Outer.A.class);
    assertThat(aClassSubject, isPresent());
    assertThat(aClassSubject.uniqueMethodWithName("hello"), isPresent());

    ClassSubject bClassSubject = inspector.clazz(Outer.B.class);
    assertThat(bClassSubject, isPresent());
    assertThat(bClassSubject.uniqueMethodWithName("world"), isPresent());

    ClassSubject cClassSubject = inspector.clazz(C.class);
    assertThat(cClassSubject, isPresent());
    assertThat(cClassSubject.uniqueMethodWithName("exclamationMark"), isPresent());
  }

  static class TestClass {

    public static void main(String[] args) {
      new Outer().test();
    }
  }

  static class Outer {

    static {
      // To ensure that we consider the class initialization of TestClass as having side effects.
      System.out.print("");
    }

    public void test() {
      new A().hello();
      new B().world(this);
      new C().exclamationMark(this);
    }

    private void hello() {
      System.out.print("Hello");
    }

    private void world() {
      System.out.print(" world");
    }

    private void exclamationMark() {
      System.out.println("!");
    }

    @NeverClassInline
    class A {

      @NeverInline
      void hello() {
        Outer.this.hello();
      }
    }

    @NeverClassInline
    @NoHorizontalClassMerging
    static class B {

      @NeverInline
      void world(Outer outer) {
        outer.world();
      }
    }
  }

  @NeverClassInline
  static class C {

    @NeverInline
    void exclamationMark(Outer outer) {
      outer.exclamationMark();
    }
  }
}
