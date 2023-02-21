// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.vertical;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.classmerging.vertical.testclasses.ForceInlineConstructorWithMultiPackageAccessesTestClasses;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ForceInlineConstructorWithMultiPackageAccessesTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ForceInlineConstructorWithMultiPackageAccessesTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(ForceInlineConstructorWithMultiPackageAccessesTest.class)
        .addInnerClasses(ForceInlineConstructorWithMultiPackageAccessesTestClasses.class)
        .addKeepMainRule(TestClass.class)
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private void inspect(CodeInspector inspector) {
    assertThat(inspector.clazz(B.class), not(isPresent()));
    // Check that C is present to ensure that B is not only removed as a result of the entire
    // object allocation being removed.
    assertThat(inspector.clazz(C.class), isPresent());
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(new C());
    }
  }

  static class B extends ForceInlineConstructorWithMultiPackageAccessesTestClasses.A {

    // B.<init>() invokes protected A.<init>() and accesses package-private field `greeting`.
    String greeting = System.currentTimeMillis() >= 0 ? "Hello world!" : null;
  }

  static class C extends B {

    @Override
    public String toString() {
      return greeting;
    }
  }
}
