// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoAccessModification;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.HorizontallyMergedClassesInspector;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InitClassToPackagePrivateFieldWithCrossPackageMergingTest extends TestBase {

  private static final String NEW_B_DESCRIPTOR = "LB;";

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(A.class, A2.class)
        .addProgramClassFileData(getProgramClassFileData())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(A.class, A2.class)
        .addProgramClassFileData(getProgramClassFileData())
        .addKeepMainRule(Main.class)
        .addHorizontallyMergedClassesInspector(
            HorizontallyMergedClassesInspector::assertNoClassesMerged)
        .enableInliningAnnotations()
        .enableNoAccessModificationAnnotationsForMembers()
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  private List<byte[]> getProgramClassFileData() throws Exception {
    return ImmutableList.of(
        transformer(Main.class)
            .replaceClassDescriptorInMethodInstructions(descriptor(B.class), NEW_B_DESCRIPTOR)
            .transform(),
        transformer(B.class).setClassDescriptor(NEW_B_DESCRIPTOR).transform());
  }

  static class Main {

    public static void main(String[] args) {
      A.foo();
      if (A2.f != 0) {
        B.bar();
      }
    }
  }

  public static class A {

    @NeverInline
    public static void foo() {
      // Will be optimized into reading A2.greeting after inlining, since the body of sayHello() is
      // empty and A2.greeting will be used to trigger the class initializer of A2.
      A2.sayHello();
    }
  }

  @NoHorizontalClassMerging
  public static class A2 {

    // Will remain due to the use in Main.main.
    @NoAccessModification static int f = (int) System.currentTimeMillis();

    static {
      System.out.print("Hello");
    }

    // Will be inlined.
    @NoAccessModification
    static void sayHello() {}
  }

  public static class /* default package */ B {

    @NeverInline
    public static void bar() {
      System.out.println(", world!");
    }
  }
}
