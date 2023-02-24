// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class VirtualInvokePrivateTest extends TestBase implements Opcodes {

  @Parameter(0)
  public TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimes()
        .withDexRuntimes()
        .withAllApiLevelsAlsoForCf()
        .build();
  }

  private static final String EXPECTED_OUTPUT = StringUtils.lines("Hello, world!", "21", "6");

  @Test
  public void testReference() throws Exception {
    assumeTrue(parameters.getRuntime().isCf());
    assumeTrue(parameters.getApiLevel().isEqualTo(AndroidApiLevel.B));

    testForJvm(parameters)
        .addProgramClassFileData(transformInvokeSpecialToInvokeVirtual())
        .run(parameters.getRuntime(), TestRunner.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addProgramClassFileData(transformInvokeSpecialToInvokeVirtual())
        .addKeepMainRule(TestRunner.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestRunner.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  private byte[] transformInvokeSpecialToInvokeVirtual() throws IOException {
    return transformer(TestRunner.class)
        .setVersion(CfVersion.V1_8)
        .transformMethodInsnInMethod(
            "main",
            ((opcode, owner, name, descriptor, isInterface, continuation) -> {
              continuation.visitMethodInsn(
                  name.equals("hello") ? Opcodes.INVOKEVIRTUAL : opcode,
                  owner,
                  name,
                  descriptor,
                  isInterface);
            }))
        .transform();
  }

  public static class TestRunner {
    private String hello() {
      return "Hello, world!";
    }

    private String hello(int i1, int i2, int i3, int i4, int i5, int i6) {
      return "" + (i1 + i2 + i3 + i4 + i5 + i6);
    }

    private String hello(long l1, long l2, long l3) {
      return "" + (l1 + l2 + l3);
    }

    public static void main(String[] args) {
      // The 3 private methods "hello" are called with invokevirtual.
      System.out.println(new TestRunner().hello());
      System.out.println(new TestRunner().hello(1, 2, 3, 4, 5, 6));
      System.out.println(new TestRunner().hello(1L, 2L, 3L));
    }
  }
}
