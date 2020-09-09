// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.keptgraph;

import static com.android.tools.r8.references.Reference.methodFromMethod;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersBuilder;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.graphinspector.GraphInspector;
import com.android.tools.r8.utils.graphinspector.GraphInspector.QueryNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KeptByReachableSubclassTest extends TestBase {

  private static final Class<?> CLASS = TestClass.class;
  private static final String EXPECTED = StringUtils.lines("C.foo!");

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return TestParametersBuilder.builder().withCfRuntimes().build();
  }

  public KeptByReachableSubclassTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    GraphInspector inspector =
        testForR8(parameters.getBackend())
            .enableGraphInspector()
            .enableNoVerticalClassMergingAnnotations()
            .enableInliningAnnotations()
            .addProgramClasses(CLASS, A.class, B.class)
            .addKeepMainRule(CLASS)
            .run(parameters.getRuntime(), CLASS)
            .assertSuccessWithOutput(EXPECTED)
            .graphInspector();

    // The only root should be the keep main-method rule.
    assertEquals(1, inspector.getRoots().size());
    inspector.rule(Origin.unknown(), 1, 1).assertRoot();

    QueryNode mainMethod =
        inspector
            .method(methodFromMethod(TestClass.class.getDeclaredMethod("main", String[].class)))
            .assertPresent();

    inspector.clazz(Reference.classFromClass(A.class)).assertPresent();
    inspector.clazz(Reference.classFromClass(B.class)).assertPresent();

    inspector
        .method(methodFromMethod(B.class.getDeclaredMethod("foo")))
        .assertOverriding(methodFromMethod(A.class.getDeclaredMethod("foo")));

    inspector.method(methodFromMethod(A.class.getDeclaredMethod("foo"))).assertKeptBy(mainMethod);
  }

  // Base class, A.foo never resolved to at runtime.
  @NoVerticalClassMerging
  public static class A {

    @NeverInline
    void foo() {
      System.out.println("A.foo!");
    }
  }

  // Actual and only instantiated type.
  @NoVerticalClassMerging
  public static class B extends A {

    @NeverInline
    @Override
    void foo() {
      System.out.println("C.foo!");
    }
  }

  public static class TestClass {

    @NeverInline
    static A create() {
      return new B();
    }

    public static void main(String[] args) {
      A a = args.length > 0 ? null : create();
      a.foo();
    }
  }
}
