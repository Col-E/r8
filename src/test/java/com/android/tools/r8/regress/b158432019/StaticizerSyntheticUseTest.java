// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.regress.b158432019;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StaticizerSyntheticUseTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public StaticizerSyntheticUseTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(Singleton.class, Main.class, Sam.class, A.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Foo", "Foo", "0");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Singleton.class, Main.class, Sam.class, A.class)
        .addKeepClassAndMembersRules(Main.class)
        .setMinApi(parameters)
        .addDontObfuscate()
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Foo", "Foo", "0");
  }

  @NeverClassInline
  public static class Singleton {

    public static final Singleton singleton = new Singleton();

    @NeverInline
    public void foo() {
      System.out.println("Foo");
    }
  }

  public interface Sam<T> {
    T foo(boolean bar);
  }

  public static class A {

    private int instanceVar = 0;

    public void caller() {
      // Ensure that the Singleton.foo method is processed to have the generated lambda being
      // processed - due to all callees being processed.
      otherChainFirst();
      Sam<Integer> f =
          b -> {
            if (b && instanceVar == 0) {
              Singleton.singleton.foo();
            }
            return instanceVar;
          };
      System.out.println(f.foo(System.nanoTime() > 0));
    }

    public void otherChainFirst() {
      otherChainSecond();
    }

    public void otherChainSecond() {
      Singleton.singleton.foo();
    }
  }

  public static class Main {

    public static void main(String[] args) {
      new A().caller();
    }
  }
}
