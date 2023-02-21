// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class Regress241478253 extends TestBase {

  static final String EXPECTED = "In the Bar";

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public Regress241478253(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Foo.class, Bar.class)
        .addKeepMainRule(Foo.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Foo.class)
        .assertSuccessWithOutputLines(EXPECTED);
    testForR8(parameters.getBackend())
        .debug()
        .addProgramClasses(Foo.class, Bar.class)
        .addKeepMainRule(Foo.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Foo.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  public static class Foo {

    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
      try {
        Class<Bar> impl =
            (Class<Bar>) Class.forName("com.android.tools.r8.regress.Regress241478253$Bar");
        impl.getDeclaredConstructor().newInstance();
      } catch (Exception ignored) {
        throw new RuntimeException(ignored);
      }
    }
  }

  public static class Bar {
    public Bar() {
      System.out.println("In the Bar");
    }
  }
}
