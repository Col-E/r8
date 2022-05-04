// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.string;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.Opcodes;

/** Reproduction for b/230821936. */
@RunWith(Parameterized.class)
public class InvokeInterfaceToStringEqualsTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassFileData(getTransformedMain())
        .addKeepMainRule(Main.class)
        .addDontOptimize()
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            parameters.isDexRuntimeVersion(Version.V8_1_0)
                && parameters.getApiLevel().isGreaterThan(AndroidApiLevel.B),
            // TODO(b/231450655): Should evaluate to "false".
            runResult -> runResult.assertSuccessWithOutputLines("true"),
            runResult -> runResult.assertSuccessWithOutputLines("false"));
  }

  private static byte[] getTransformedMain() throws IOException {
    return transformer(Main.class)
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

  static class Main {

    public static void main(String[] args) {
      String s = System.currentTimeMillis() > 0 ? "foo" : "bar";
      System.out.println(s.equals("baz"));
    }
  }
}
