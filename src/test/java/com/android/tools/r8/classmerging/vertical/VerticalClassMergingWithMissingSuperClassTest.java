// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.vertical;


import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class VerticalClassMergingWithMissingSuperClassTest extends TestBase {

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, A.class, B.class, C.class)
        .addKeepMainRule(Main.class)
        .addDontWarn(MissingClass.class)
        .addVerticallyMergedClassesInspector(
            inspector -> inspector.assertMergedIntoSubtype(B.class))
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .addRunClasspathFiles(buildOnDexRuntime(parameters, MissingClass.class))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("C", "A", "B");
  }

  static class Main {
    public static void main(String[] args) {
      new C().m();
    }
  }

  static class MissingClass {}

  @NoVerticalClassMerging
  static class A extends MissingClass {
    void m() {
      System.out.println("A");
    }
  }

  static class B extends A {
    @Override
    void m() {
      super.m();
      System.out.println("B");
    }
  }

  static class C extends B {
    C() {
      System.out.println("C");
    }
  }
}
