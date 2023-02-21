// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resolution;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class PrivateInvokeVirtualTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public PrivateInvokeVirtualTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  public static class UInt implements Comparable<UInt> {

    private final int val;

    public UInt(int val) {
      this.val = val;
    }

    private int compareToHelper(int i) {
      return Integer.compare(val, i);
    }

    @Override
    public int compareTo(UInt o) {
      return compareToHelper(o.val);
    }
  }

  public static class Main {

    public static void main(String[] args) {
      System.out.println(new UInt(3).compareTo(new UInt(args.length)));
    }
  }

  @Test
  public void testRuntime() throws IOException, CompilationFailedException, ExecutionException {
    testForRuntime(parameters)
        .addProgramClassFileData(getUIntWithTransformedInvoke())
        .addProgramClasses(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("1");
  }

  @Test
  public void testPrivateInvokeVirtual()
      throws IOException, CompilationFailedException, ExecutionException {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class)
        .addProgramClassFileData(getUIntWithTransformedInvoke())
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("1");
  }

  private byte[] getUIntWithTransformedInvoke() throws IOException {
    return transformer(UInt.class)
        .transformMethodInsnInMethod(
            "compareTo",
            (opcode, owner, name, descriptor, isInterface, continuation) -> {
              if (name.contains("compareToHelper")) {
                assertEquals(Opcodes.INVOKESPECIAL, opcode);
                continuation.visitMethodInsn(
                    Opcodes.INVOKEVIRTUAL, owner, name, descriptor, isInterface);
              } else {
                continuation.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
              }
            })
        .transform();
  }
}
