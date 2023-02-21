// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** Regression test for b/120182628. */
@RunWith(Parameterized.class)
public class BuilderWithInheritanceTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public BuilderWithInheritanceTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    CodeInspector inspector =
        testForR8(parameters.getBackend())
            .addInnerClasses(BuilderWithInheritanceTest.class)
            .addKeepMainRule(TestClass.class)
            .enableInliningAnnotations()
            .enableNoVerticalClassMergingAnnotations()
            .setMinApi(parameters)
            .run(parameters.getRuntime(), TestClass.class)
            .assertSuccessWithOutput("42")
            .inspector();
    assertThat(inspector.clazz(Builder.class), not(isPresent()));
  }

  static class TestClass {

    public static void main(String[] args) {
      A a = new Builder(42).build();
      System.out.print(a.get());
    }
  }

  static class A {

    private final int f;

    public A(int f) {
      this.f = f;
    }

    public int get() {
      return f;
    }
  }

  @NoVerticalClassMerging
  static class BuilderBase {

    protected int f;

    public BuilderBase(int f) {
      this.f = f;
    }

    @NeverInline
    public static int get(BuilderBase obj) {
      return obj.f;
    }
  }

  static class Builder extends BuilderBase {

    public Builder(int f) {
      super(f);
    }

    public A build() {
      // After force inlining of get() there will be a field-read "this.f", which is not allowed by
      // the class inliner, because the class inliner only handles reads from fields that are
      // declared on Builder.
      return new A(get(this));
    }
  }
}
