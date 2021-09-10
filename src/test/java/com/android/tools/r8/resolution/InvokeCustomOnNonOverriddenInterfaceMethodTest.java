// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resolution;

import static com.android.tools.r8.references.Reference.classFromClass;
import static com.android.tools.r8.references.Reference.methodFromMethod;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.utils.structural.Ordered;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.WrongMethodTypeException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;

/** Regression for b/116283747. */
@RunWith(Parameterized.class)
public class InvokeCustomOnNonOverriddenInterfaceMethodTest extends TestBase {

  private static final String[] EXPECTED = new String[] {"I.target"};

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withAllRuntimes()
        .withApiLevelsStartingAtIncluding(
            Ordered.max(apiLevelWithInvokeCustomSupport(), apiLevelWithConstMethodHandleSupport()))
        .build();
  }

  public InvokeCustomOnNonOverriddenInterfaceMethodTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRuntime() throws Throwable {
    testForRuntime(parameters)
        .addProgramClasses(Main.class, I.class, Super.class)
        .addProgramClassFileData(getInvokeCustomTransform())
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkRunResult);
  }

  private void checkRunResult(SingleTestRunResult<?> result) {
    if (parameters.isCfRuntime()
        || parameters.asDexRuntime().getVersion().isNewerThanOrEqual(Version.V10_0_0)) {
      result.assertSuccessWithOutputLines(EXPECTED);
    } else {
      // Fails due to b/115964401.
      assertEquals(Version.V9_0_0, parameters.getDexRuntimeVersion());
      result.assertFailureWithErrorThatThrows(WrongMethodTypeException.class);
    }
  }

  private static byte[] getInvokeCustomTransform() throws Throwable {
    ClassReference symbolicHolder = classFromClass(InvokeCustom.class);
    MethodReference method = methodFromMethod(InvokeCustom.class.getMethod("target"));
    return transformer(InvokeCustom.class)
        .transformMethodInsnInMethod(
            "test",
            (opcode, owner, name, descriptor, isInterface, visitor) -> {
              // Replace null argument by a const method handle.
              visitor.visitInsn(Opcodes.POP);
              visitor.visitLdcInsn(
                  new Handle(
                      Opcodes.H_INVOKEVIRTUAL,
                      symbolicHolder.getBinaryName(),
                      method.getMethodName(),
                      method.getMethodDescriptor(),
                      false));
              visitor.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            })
        .transform();
  }

  interface I {
    default void target() {
      System.out.println("I.target");
    }
  }

  static class Super implements I {}

  static class InvokeCustom extends Super {

    public static void doInvokeExact(MethodHandle handle) throws Throwable {
      handle.invokeExact(new InvokeCustom());
    }

    public static void test() throws Throwable {
      doInvokeExact(null /* will be const method handle */);
    }
  }

  static class Main {

    public static void main(String[] args) throws Throwable {
      InvokeCustom.test();
    }
  }
}
