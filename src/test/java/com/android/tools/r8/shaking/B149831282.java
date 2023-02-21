// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class B149831282 extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public B149831282(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .apply(this::addProgramInputs)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("In A.m()");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .apply(this::addProgramInputs)
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("In A.m()");
  }

  private void addProgramInputs(TestBuilder<?, ?> builder) throws Exception {
    builder
        .addProgramClasses(A.class, B.class)
        .addProgramClassFileData(
            transformer(TestClass.class)
                .transformTypeInsnInMethod(
                    "main",
                    (opcode, type, continuation) -> {
                      assertEquals(binaryName(C.class), type);
                      continuation.visitTypeInsn(opcode, "b149831282/C");
                    })
                .transformMethodInsnInMethod(
                    "main",
                    (opcode, owner, name, descriptor, isInterface, continuation) -> {
                      assertEquals(binaryName(C.class), owner);
                      continuation.visitMethodInsn(
                          opcode, "b149831282/C", name, descriptor, isInterface);
                    })
                .transform())
        .addProgramClassFileData(
            transformer(C.class).setClassDescriptor("Lb149831282/C;").transform());
  }

  static class TestClass {

    public static void main(String[] args) {
      new C().m();
    }
  }

  @NoVerticalClassMerging
  static class A {

    @NeverInline
    protected void m() {
      System.out.println("In A.m()");
    }
  }

  @NoVerticalClassMerging
  public static class B extends A {}

  @NeverClassInline
  public static class /*b149831282.*/ C extends B {

    @NeverInline
    @Override
    public void m() {
      super.m();
    }
  }
}
