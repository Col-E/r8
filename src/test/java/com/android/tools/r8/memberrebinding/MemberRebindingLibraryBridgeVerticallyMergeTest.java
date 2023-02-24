// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.memberrebinding;


import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MemberRebindingLibraryBridgeVerticallyMergeTest extends TestBase {

  private final String[] EXPECTED = new String[] {"Lib::foo", "B::foo"};

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClasses(A.class, B.class, C.class, D.class, Main.class)
        .addLibraryClasses(Lib.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .addProgramClasses(A.class, B.class, C.class, D.class, Main.class)
        .addLibraryClasses(Lib.class)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters)
        .compile()
        .addBootClasspathClasses(Lib.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(A.class, B.class, C.class, D.class, Main.class)
        .addLibraryClasses(Lib.class)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .addKeepClassRules(C.class, D.class)
        .enableInliningAnnotations()
        .allowAccessModification()
        .compile()
        .addBootClasspathClasses(Lib.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  public static class Lib {

    public void foo() {
      System.out.println("Lib::foo");
    }
  }

  public static class A extends Lib {}

  public static class B extends A {

    @Override
    @NeverInline
    public void foo() {
      super.foo();
      System.out.println("B::foo");
    }
  }

  public static class C extends B {}

  public static class D extends B {}

  public static class Main {

    public static void main(String[] args) {
      callFoo(System.currentTimeMillis() > 0 ? new C() : new B());
    }

    public static void callFoo(B b) {
      b.foo();
    }
  }
}
