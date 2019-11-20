// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import static org.hamcrest.CoreMatchers.containsString;
import static org.objectweb.asm.Opcodes.ACC_FINAL;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.transformers.ClassTransformer;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.MethodVisitor;

@RunWith(Parameterized.class)
public class InvokeVirtualFinalTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public InvokeVirtualFinalTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testInvokeSpecialOnClassWithFinal()
      throws ExecutionException, CompilationFailedException, IOException {
    String expectedError = "overrides final";
    if (parameters.isDexRuntime()
        && parameters.getRuntime().asDex().getVm().isNewerThan(DexVm.ART_4_4_4_HOST)
        && parameters.getRuntime().asDex().getVm().isOlderThanOrEqual(DexVm.ART_6_0_1_HOST)) {
      expectedError = "LinkageError";
    }
    testForRuntime(parameters)
        .addProgramClasses(B.class, Main.class)
        .addProgramClassFileData(getClassWithTransformedInvoked())
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatMatches(containsString(expectedError));
  }

  private byte[] getClassWithTransformedInvoked() throws IOException {
    return transformer(A.class)
        .addClassTransformer(
            new ClassTransformer() {
              @Override
              public MethodVisitor visitMethod(
                  int access,
                  String name,
                  String descriptor,
                  String signature,
                  String[] exceptions) {
                if (name.equals("foo")) {
                  access |= ACC_FINAL;
                }
                return super.visitMethod(access, name, descriptor, signature, exceptions);
              }
            })
        .transform();
  }

  public static class A {
    public void foo() {
      System.out.println("Hello from A");
    }
  }

  public static class B extends A {
    public void foo() {
      System.out.println("Hello from B");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      new B().foo();
      ((A) new B()).foo();
    }
  }
}
