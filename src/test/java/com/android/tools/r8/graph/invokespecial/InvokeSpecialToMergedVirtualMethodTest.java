// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.invokespecial;

import static org.junit.Assert.assertEquals;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvokeSpecialToMergedVirtualMethodTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(Main.class, MergeIntoA.class)
        .addProgramClassFileData(getClassWithTransformedInvoked())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A.foo()");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, MergeIntoA.class)
        .addProgramClassFileData(getClassWithTransformedInvoked())
        .addKeepMainRule(Main.class)
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector
                    .assertMergedInto(MergeIntoA.class, MergeIntoA.class)
                    .assertNoOtherClassesMerged())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A.foo()");
  }

  private byte[] getClassWithTransformedInvoked() throws IOException {
    return transformer(A.class)
        .transformMethodInsnInMethod(
            "bar",
            (opcode, owner, name, descriptor, isInterface, continuation) -> {
              assertEquals(INVOKEVIRTUAL, opcode);
              assertEquals("notify", name);
              continuation.visitMethodInsn(
                  INVOKESPECIAL, binaryName(A.class), "foo", descriptor, isInterface);
            })
        .transform();
  }

  public static class A {

    public void foo() {
      System.out.println("A.foo()");
    }

    public void bar() {
      // Will be rewritten to invoke-special A.foo(), which is merged with MergeIntoA.foo().
      notify();
    }
  }

  public static class MergeIntoA {

    public void foo() {
      System.out.println("B.foo()");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      new A().bar();
    }
  }
}
