// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.code.invokedynamic;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.StringUtils;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;

/**
 * Test invokedynamic with a static bootstrap method with an extra arg that is a MethodHandle of
 * kind invoke virtual. The target method is a method into a class implementing an abstract method
 * and that shadows a default method from an interface.
 */
// TODO(b/167145686): Copy this test and implement all of the variants in
//  ...AndroidOTest.invokeCustom... and then delete those tests.
@RunWith(Parameterized.class)
public class InvokeDynamicVirtualDispatchToDefaultInterfaceMethodTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("Called I.foo");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withAllRuntimes()
        .withApiLevelsStartingAtIncluding(apiLevelWithInvokeCustomSupport())
        .enableApiLevelsForCf()
        .build();
  }

  public InvokeDynamicVirtualDispatchToDefaultInterfaceMethodTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testReference() throws Exception {
    testForDesugaring(parameters)
        .addProgramClasses(I.class, A.class)
        .addProgramClassFileData(getTransformedTestClass())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    assumeTrue(
        "Only test one R8/CF build.",
        parameters.isDexRuntime() || parameters.getApiLevel() == apiLevelWithInvokeCustomSupport());
    testForR8(parameters.getBackend())
        .allowAccessModification()
        .addProgramClasses(I.class, A.class)
        .addProgramClassFileData(getTransformedTestClass())
        .setMinApi(parameters)
        .addKeepMainRule(TestClass.class)
        .addKeepRules("-keepclassmembers class * { *** foo(...); }")
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  private byte[] getTransformedTestClass() throws Exception {
    ClassReference aClass = Reference.classFromClass(A.class);
    MethodReference iFoo = Reference.methodFromMethod(I.class.getDeclaredMethod("foo"));
    MethodReference bsm =
        Reference.methodFromMethod(
            TestClass.class.getDeclaredMethod(
                "bsmCreateCallSite",
                Lookup.class,
                String.class,
                MethodType.class,
                MethodHandle.class));
    return transformer(TestClass.class)
        .transformMethodInsnInMethod(
            "main",
            (opcode, owner, name, descriptor, isInterface, visitor) -> {
              if (name.equals("replaced")) {
                visitor.visitInvokeDynamicInsn(
                    iFoo.getMethodName(),
                    "(" + aClass.getDescriptor() + ")V",
                    new Handle(
                        Opcodes.H_INVOKESTATIC,
                        bsm.getHolderClass().getBinaryName(),
                        bsm.getMethodName(),
                        bsm.getMethodDescriptor(),
                        false),
                    new Handle(
                        Opcodes.H_INVOKEVIRTUAL,
                        aClass.getBinaryName(),
                        iFoo.getMethodName(),
                        iFoo.getMethodDescriptor(),
                        false));
              } else {
                visitor.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
              }
            })
        .transform();
  }

  public interface I {

    default void foo() {
      System.out.println("Called I.foo");
    }
  }

  public static class A implements I {
    // Instantiation with default from I.
  }

  static class TestClass {

    public static CallSite bsmCreateCallSite(
        MethodHandles.Lookup caller, String name, MethodType type, MethodHandle handle) {
      return new ConstantCallSite(handle);
    }

    public static void replaced(Object o) {
      throw new RuntimeException("unreachable!");
    }

    public static void main(String[] args) {
      replaced(new A());
    }
  }
}
