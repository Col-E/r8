// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.dynamictype;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
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
public class DynamicUpperBoundWithEffectivelyFinalTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public DynamicUpperBoundWithEffectivelyFinalTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws ExecutionException, CompilationFailedException, IOException {
    testForR8(parameters.getBackend())
        .addProgramClasses(A.class, Base.class, Final.class, Main.class)
        .addKeepMainRule(Main.class)
        .addKeepClassRules(Final.class)
        .addKeepClassAndMembersRules(Base.class)
        .setMinApi(parameters)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello World!");
  }

  @NeverClassInline
  public static class A {

    @Override
    public String toString() {
      return "Hello World!";
    }
  }

  public abstract static class Base {

    abstract A run();
  }

  @NeverClassInline
  public static final class Final extends Base {

    @Override
    @NeverInline
    A run() {
      return new A();
    }
  }

  public static class Main {

    public static void main(String[] args) {
      System.out.println(runFinal(new Final()));
    }

    @NeverInline
    public static A runFinal(Final fin) {
      return fin.run();
    }
  }
}
