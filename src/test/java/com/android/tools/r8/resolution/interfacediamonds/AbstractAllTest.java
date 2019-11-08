// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution.interfacediamonds;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class AbstractAllTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public AbstractAllTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addInnerClasses(AbstractAllTest.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("C::f");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(AbstractAllTest.class)
        .addKeepMainRule(Main.class)
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("C::f");
  }

  public interface T {
    void f();
  }

  public interface L extends T {
    @Override
    void f();
  }

  public interface R extends T {
    @Override
    void f();
  }

  public abstract static class B implements L, R {
    // Intentionally empty.
    // Resolving B::f can give any one of T::f, L::f or R::f.
  }

  public static class C extends B {

    @Override
    public void f() {
      System.out.println("C::f");
    }
  }

  static class Main {
    public static void main(String[] args) {
      B b = args.length == 42 ? null : new C();
      b.f();
    }
  }
}
