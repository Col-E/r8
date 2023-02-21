// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.invokespecial;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.DescriptorUtils;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

// This is a reproduction of b/144450911.
@RunWith(Parameterized.class)
public class InvokeSpecialMissingInvokeVirtualTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public InvokeSpecialMissingInvokeVirtualTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters.getRuntime(), parameters.getApiLevel())
        .addProgramClasses(A.class, Main.class)
        .addProgramClassFileData(getClassWithTransformedInvoked())
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatMatches(containsString("NoSuchMethodError"));
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(A.class, Main.class)
        .addProgramClassFileData(getClassWithTransformedInvoked())
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatMatches(containsString("NoSuchMethodError"));
  }

  private byte[] getClassWithTransformedInvoked() throws IOException {
    return transformer(B.class)
        .transformMethodInsnInMethod(
            "bar",
            (opcode, owner, name, descriptor, isInterface, continuation) -> {
              assertEquals(INVOKEVIRTUAL, opcode);
              assertEquals("notify", name);
              continuation.visitMethodInsn(
                  INVOKESPECIAL,
                  DescriptorUtils.getBinaryNameFromJavaType(A.class.getTypeName()),
                  "foo",
                  descriptor,
                  isInterface);
            })
        .transform();
  }

  public static class A {}

  public static class B extends A {

    public void bar() {
      notify(); // Will be rewritten to invoke-special A.foo() which is missing.
    }
  }

  public static class Main {

    public static void main(String[] args) {
      new B().bar();
    }
  }
}
