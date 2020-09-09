// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.keptgraph;

import static com.android.tools.r8.references.Reference.methodFromMethod;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.graphinspector.GraphInspector;
import com.android.tools.r8.utils.graphinspector.GraphInspector.QueryNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KeptByTwoMethods extends TestBase {

  @NoVerticalClassMerging
  public static class A {

    @NeverInline
    void foo() {
      System.out.println("A.foo!");
    }
  }

  @NoVerticalClassMerging
  public static class B extends A {
    // Intermediate to A.
  }

  public static class TestClass {

    @NeverInline
    static void bar(B b) {
      b.foo();
    }

    @NeverInline
    static void baz(B b) {
      b.foo();
    }

    public static void main(String[] args) {
      bar(new B());
      baz(new B());
    }
  }

  private static final String EXPECTED = StringUtils.lines("A.foo!", "A.foo!");

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntimes().build();
  }

  private final TestParameters parameters;

  public KeptByTwoMethods(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    GraphInspector inspector =
        testForR8(parameters.getBackend())
            .enableGraphInspector()
            .enableNoVerticalClassMergingAnnotations()
            .enableInliningAnnotations()
            .addKeepMainRule(TestClass.class)
            .addProgramClasses(TestClass.class, A.class, B.class)
            .run(parameters.getRuntime(), TestClass.class)
            .assertSuccessWithOutput(EXPECTED)
            .graphInspector();

    MethodReference barRef = methodFromMethod(TestClass.class.getDeclaredMethod("bar", B.class));
    inspector.method(barRef).assertPresent();

    MethodReference bazRef = methodFromMethod(TestClass.class.getDeclaredMethod("baz", B.class));
    inspector.method(bazRef).assertPresent();

    QueryNode foo =
        inspector.method(methodFromMethod(A.class.getDeclaredMethod("foo"))).assertPresent();

    foo.assertInvokedFrom(barRef);
    foo.assertInvokedFrom(bazRef);
  }
}
