// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** This is a reproduction of b/148313389, where we rewrite the super-call in B.<init>. */
@RunWith(Parameterized.class)
public class VerticalClassMergerInvokeSpecialInConstructorTest extends TestBase {

  public static final String[] EXPECTED =
      new String[] {"A.<init>", "B.<init>", "A.foo", "C.<init>", "B.foo"};

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public VerticalClassMergerInvokeSpecialInConstructorTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRuntime() throws IOException, CompilationFailedException, ExecutionException {
    testForRuntime(parameters)
        .addInnerClasses(VerticalClassMergerInvokeSpecialInConstructorTest.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws IOException, CompilationFailedException, ExecutionException {
    testForR8(parameters.getBackend())
        .addInnerClasses(VerticalClassMergerInvokeSpecialInConstructorTest.class)
        .addKeepMainRule(Main.class)
        .addKeepClassRules(A.class)
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  public abstract static class A {

    public A() {
      System.out.println("A.<init>");
    }

    public void foo() {
      System.out.println("A.foo");
    }
  }

  @NeverClassInline
  public static class B extends A {

    public B() {
      System.out.println("B.<init>");
      super.foo(); // <-- In b/148313389, this was rewritten to invoke-direct B.foo.
    }

    @Override
    public void foo() {
      System.out.println("B.foo");
    }
  }

  @NeverClassInline
  public static class C extends B {

    public C() {
      System.out.println("C.<init>");
      super.foo();
    }
  }

  public static class Main {

    public static void main(String[] args) {
      new C();
    }
  }
}
