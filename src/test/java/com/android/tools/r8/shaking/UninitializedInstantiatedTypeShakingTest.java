// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NoHorizontalClassMerging;
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
public class UninitializedInstantiatedTypeShakingTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().build();
  }

  public UninitializedInstantiatedTypeShakingTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(UninitializedInstantiatedTypeShakingTest.class)
        .addKeepMainRule(TestClass.class)
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters.getRuntime())
        .compile()
        .inspect(UninitializedInstantiatedTypeShakingTest::verifyOutput)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("A.<clinit>()", "B.method()");
  }

  private static void verifyOutput(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(A.class);
    assertThat(classSubject.init(), not(isPresent()));
    // TODO(b/132669230): A.method() should be pruned.
    assertThat(classSubject.uniqueMethodWithName("method"), isPresent());
  }

  static class TestClass {

    public static void main(String[] args) {
      // Instantiate A. The instantiation cannot be removed because the class initialization of A
      // has side effects. However, the constructor call to A.<init>() can be removed, since it has
      // no side effects.
      new A();

      // Invoke I.m(). Since we remove the call to A.<init>(), there are no more initialized
      // instances of A. Therefore, the method invocation below should never be able to hit A.m().
      I obj = System.currentTimeMillis() >= 0 ? new B() : new C();
      obj.method();
    }
  }

  interface I {

    void method();
  }

  static class A implements I {

    static {
      System.out.println("A.<clinit>()");
    }

    @Override
    public void method() {
      System.out.println("A.method()");
    }
  }

  @NoHorizontalClassMerging
  static class B implements I {

    @Override
    public void method() {
      System.out.println("B.method()");
    }
  }

  @NoHorizontalClassMerging
  static class C implements I {

    @Override
    public void method() {
      System.out.println("C.method()");
    }
  }
}
