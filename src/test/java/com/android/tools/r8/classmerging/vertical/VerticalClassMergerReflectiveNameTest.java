// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.vertical;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class VerticalClassMergerReflectiveNameTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimes()
        .withDexRuntimesStartingFromIncluding(Version.V8_1_0)
        .withAllApiLevelsAlsoForCf()
        .build();
  }

  public VerticalClassMergerReflectiveNameTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(Main.class, A.class, B.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A::foo", "B::foo");
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, A.class, B.class)
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A::foo", "B::foo");
  }

  public static class A {

    @NeverInline
    public void foo() {
      System.out.println("A::foo");
    }
  }

  @NeverClassInline
  public static class B extends A {

    @NeverInline
    public void bar() {
      System.out.println("B::foo");
    }
  }

  public static class Main {

    private static final String className =
        "com.android.tools.r8.classmerging.vertical.VerticalClassMergerReflectiveNameTest$A";

    static {
      try {
        Class.forName(className);
      } catch (ClassNotFoundException e) {
      }
    }

    public static void main(String[] args) {
      B b = new B();
      b.foo();
      b.bar();
    }
  }
}
