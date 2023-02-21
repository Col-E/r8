// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IntersectionWithInheritanceLambdaTest extends TestBase {

  private static final String[] EXPECTED = new String[] {"Lambda.foo", "J.bar", "K.baz"};

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public IntersectionWithInheritanceLambdaTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRuntime() throws IOException, CompilationFailedException, ExecutionException {
    testForRuntime(parameters)
        .addInnerClasses(IntersectionWithInheritanceLambdaTest.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws IOException, CompilationFailedException, ExecutionException {
    testForR8(parameters.getBackend())
        .addInnerClasses(IntersectionWithInheritanceLambdaTest.class)
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  public interface J {
    default void bar() {
      System.out.println("J.bar");
    }
  }

  public interface K extends J {
    void foo();

    default void baz() {
      System.out.println("K.baz");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      callFooBarBaz(() -> System.out.println("Lambda.foo"));
    }

    private static void callFooBarBaz(K k) {
      k.foo();
      k.bar();
      k.baz();
    }
  }
}
