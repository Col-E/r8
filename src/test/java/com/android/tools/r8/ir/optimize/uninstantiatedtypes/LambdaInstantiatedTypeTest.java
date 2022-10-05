// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.uninstantiatedtypes;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Tests that the interface {@link CustomSupplier}, which is only instantiated (indirectly) via a
 * lambda, is not considered to be uninstantiated.
 */
@RunWith(Parameterized.class)
public class LambdaInstantiatedTypeTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return TestParameters.builder().withAllRuntimesAndApiLevels().build();
  }

  public LambdaInstantiatedTypeTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  String expected = StringUtils.joinLines("In TestClass.live()");

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addInnerClasses(LambdaInstantiatedTypeTest.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(expected);
  }

  @Test
  public void test() throws Exception {
    CodeInspector inspector =
        testForR8(parameters.getBackend())
            .addInnerClasses(LambdaInstantiatedTypeTest.class)
            .addKeepMainRule(TestClass.class)
            .enableInliningAnnotations()
            .setMinApi(parameters.getApiLevel())
            .run(parameters.getRuntime(), TestClass.class)
            .assertSuccessWithOutput(expected)
            .inspector();

    ClassSubject classSubject = inspector.clazz(TestClass.class);
    assertThat(classSubject, isPresent());

    // Check that the method live() has not been removed.
    assertThat(classSubject.uniqueMethodWithOriginalName("live"), isPresent());
  }

  static class TestClass {

    public static void main(String[] args) {
      testNoRewriteToThrowNull();
    }

    @NeverInline
    private static void testNoRewriteToThrowNull() {
      // Should not be rewritten to "throw null".
      A.create();
      live();
    }

    @NeverInline
    private static void live() {
      System.out.print("In TestClass.live()");
    }
  }

  static class A {

    private static final CustomSupplier<A> factory = A::new;

    @NeverInline
    public static A create() {
      // Should not be rewritten to throw null since CustomSupplier<A> is instantiated by a lambda.
      return factory.get();
    }
  }

  interface CustomSupplier<T> {

    T get();
  }
}
