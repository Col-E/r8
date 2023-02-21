// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.sideeffect;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ArrayOfObjectsCreationCanBePostponedTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ArrayOfObjectsCreationCanBePostponedTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(ArrayOfObjectsCreationCanBePostponedTest.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(A.class);
    assertThat(classSubject, isPresent());

    // We should have been able to inline A.inlineable() since A.<clinit>() can safely be postponed.
    assertThat(classSubject.uniqueMethodWithOriginalName("inlineable"), not(isPresent()));
  }

  static class TestClass {

    public static void main(String[] args) {
      A.inlineable();
      System.out.println(A.values[0].getMessage());
    }
  }

  static class A {

    static A[] values = new A[] {new A(" world!")};

    String message;

    A(String message) {
      this.message = message;
    }

    static void inlineable() {
      System.out.print("Hello");
    }

    String getMessage() {
      return message;
    }
  }
}
