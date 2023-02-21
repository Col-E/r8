// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.lambdas;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class LambdaWithPrivateInterfaceInvokeTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("Hello world");

  private final TestParameters parameters;
  private final boolean useInvokeSpecial;

  @Parameterized.Parameters(name = "{0}, invokespecial:{1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  public LambdaWithPrivateInterfaceInvokeTest(TestParameters parameters, boolean useInvokeSpecial) {
    this.parameters = parameters;
    this.useInvokeSpecial = useInvokeSpecial;
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(TestClass.class, MyFun.class, A.class)
        .addProgramClassFileData(getTransformForI())
        .run(parameters.getRuntime(), TestClass.class)
        // On JDK 8 and 9 the VM will fail if not targeting with invoke special.
        .applyIf(
            !useInvokeSpecial
                && parameters.isCfRuntime()
                && parameters.asCfRuntime().isOlderThan(CfVm.JDK11),
            r -> r.assertFailureWithErrorThatThrows(IncompatibleClassChangeError.class),
            r -> r.assertSuccessWithOutput(EXPECTED));
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class, MyFun.class, A.class)
        .addProgramClassFileData(getTransformForI())
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  private byte[] getTransformForI() throws Exception {
    return transformer(I.class)
        .setPrivate(I.class.getDeclaredMethod("bar"))
        .transformMethodInsnInMethod(
            "lambda$foo$0",
            (opcode, owner, name, descriptor, isInterface, visitor) -> {
              if (name.equals("bar")) {
                assertEquals(Opcodes.INVOKEINTERFACE, opcode);
                visitor.visitMethodInsn(
                    useInvokeSpecial ? Opcodes.INVOKESPECIAL : Opcodes.INVOKEINTERFACE,
                    owner,
                    name,
                    descriptor,
                    isInterface);
              } else {
                visitor.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
              }
            })
        .transform();
  }

  interface I {
    /* private */ default String bar() {
      return "Hello world";
    }

    default void foo() {
      TestClass.run(
          () -> {
            System.out.println(bar());
          });
    }
  }

  interface MyFun {
    void run();
  }

  static class A implements I {}

  static class TestClass {

    public static void run(MyFun fn) {
      fn.run();
    }

    public static void main(String[] args) {
      new A().foo();
    }
  }
}
