// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.string;


import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.Opcodes;

/** Reproduction for b/230821936. Also see b/231450655 for Art 8.1 issue. */
@RunWith(Parameterized.class)
public class InvokeInterfaceToStringEqualsTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .addProgramClassFileData(getTransformedMain(MainD8.class))
        .setMinApi(parameters)
        .run(parameters.getRuntime(), MainD8.class)
        .assertSuccessWithOutputLines("false");
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassFileData(getTransformedMain(MainR8.class))
        .addKeepMainRule(MainR8.class)
        .addDontOptimize()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), MainR8.class)
        .assertSuccessWithOutputLines("false");
  }

  private static byte[] getTransformedMain(Class<?> clazz) throws IOException {
    return transformer(clazz)
        .transformMethodInsnInMethod(
            "main",
            (opcode, owner, name, descriptor, isInterface, continuation) -> {
              if (name.equals("equals")) {
                continuation.visitMethodInsn(
                    Opcodes.INVOKEINTERFACE,
                    binaryName(CharSequence.class),
                    name,
                    descriptor,
                    true);
              } else {
                continuation.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
              }
            })
        .transform();
  }

  static class MainR8 {

    public static void main(String[] args) {
      String s = System.currentTimeMillis() > 0 ? "foo" : "bar";
      System.out.println(s.equals("baz"));
    }
  }

  static class MainD8 {

    public static void main(String[] args) {
      System.out.println("foo".equals("baz"));
    }
  }
}
