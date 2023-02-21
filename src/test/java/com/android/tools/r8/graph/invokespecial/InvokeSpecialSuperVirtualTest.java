// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.invokespecial;

import static com.android.tools.r8.utils.DescriptorUtils.getBinaryNameFromJavaType;
import static org.junit.Assert.assertEquals;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvokeSpecialSuperVirtualTest extends TestBase {

  private static final String EXPECTED_RESULT =
      StringUtils.lines("A", "B", "A", "C", "A", "B", "A", "B", "A");

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public InvokeSpecialSuperVirtualTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters.getRuntime(), parameters.getApiLevel())
        .addProgramClasses(A.class, C.class, Main.class)
        .addProgramClassFileData(getClassBWithTransformedInvoked())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(A.class, C.class, Main.class)
        .addProgramClassFileData(getClassBWithTransformedInvoked())
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  private byte[] getClassBWithTransformedInvoked() throws IOException {
    return transformer(B.class)
        .transformMethodInsnInMethod(
            "printAll",
            (opcode, owner, name, descriptor, isInterface, continuation) -> {
              if (name.equals("replace")) {
                assertEquals(INVOKESPECIAL, opcode);
                assertEquals(getBinaryNameFromJavaType(B.class.getTypeName()), owner);
                continuation.visitMethodInsn(opcode, owner, "print", descriptor, isInterface);
              } else {
                continuation.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
              }
            })
        .transform();
  }

  public static class A {

    void print() {
      System.out.println("A");
    }
  }

  public static class B extends A {

    void print() {
      System.out.println("B");
      super.print();
    }

    private void replace() {
      System.out.println("Should not be called.");
    }

    void printAll() {
      // Invoke-super.
      super.print();
      // Rewritten to Invoke-special to B::print.
      replace();
      // Invoke-virtual.
      print();
    }
  }

  public static class C extends B {

    void print() {
      System.out.println("C");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      new C().printAll();
      new B().printAll();
    }
  }
}
