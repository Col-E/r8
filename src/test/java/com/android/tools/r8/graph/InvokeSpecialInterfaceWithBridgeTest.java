// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.utils.DescriptorUtils;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

// This is a reproduction of b/144450911.
@RunWith(Parameterized.class)
public class InvokeSpecialInterfaceWithBridgeTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public InvokeSpecialInterfaceWithBridgeTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRuntime() throws IOException, CompilationFailedException, ExecutionException {
    TestRunResult<?> runResult =
        testForRuntime(parameters.getRuntime(), parameters.getApiLevel())
            .addProgramClasses(I.class, A.class, Main.class)
            .addProgramClassFileData(getClassWithTransformedInvoked())
            .run(parameters.getRuntime(), Main.class);
    // TODO(b/144450911): Remove when fixed.
    if (parameters.isCfRuntime()) {
      runResult.assertSuccessWithOutputLines("Hello World!");
    } else if (parameters.isDexRuntime()
        && parameters.getRuntime().asDex().getVm().isOlderThanOrEqual(DexVm.ART_4_4_4_TARGET)) {
      runResult.assertFailureWithErrorThatMatches(containsString("NoSuchMethodError"));
    } else {
      runResult.assertFailureWithErrorThatMatches(
          anyOf(
              containsString("IncompatibleClassChangeError"),
              containsString(
                  "com.android.tools.r8.graph.InvokeSpecialForInvokeVirtualTest$B.foo")));
    }
  }

  private byte[] getClassWithTransformedInvoked() throws IOException {
    return transformer(B.class)
        .transformMethodInsnInMethod(
            "bar",
            (opcode, owner, name, descriptor, isInterface, continuation) -> {
              assertEquals(INVOKEVIRTUAL, opcode);
              assertEquals(owner, DescriptorUtils.getBinaryNameFromJavaType(B.class.getTypeName()));
              continuation.apply(INVOKESPECIAL, owner, name, descriptor, isInterface);
            })
        .transform();
  }

  public interface I {
    default void foo() {
      System.out.println("Hello World!");
    }
  }

  public static class A implements I {}

  public static class B extends A {

    public void bar() {
      foo(); // Will be rewritten to invoke-special A.foo()
    }
  }

  public static class Main {

    public static void main(String[] args) {
      new B().bar();
    }
  }
}
