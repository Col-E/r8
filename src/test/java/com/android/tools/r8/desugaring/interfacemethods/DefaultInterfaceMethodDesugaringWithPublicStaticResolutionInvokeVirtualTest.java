// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugaring.interfacemethods;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class DefaultInterfaceMethodDesugaringWithPublicStaticResolutionInvokeVirtualTest
    extends TestBase {

  private static final String EXPECTED = StringUtils.lines("I.m()");
  private static final String EXPECTED_R8 = StringUtils.lines("B.m()");

  private final TestParameters parameters;
  private final boolean invalidInvoke;

  @Parameterized.Parameters(name = "{0}, invalid:{1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build(),
        BooleanUtils.values());
  }

  public DefaultInterfaceMethodDesugaringWithPublicStaticResolutionInvokeVirtualTest(
      TestParameters parameters, boolean invalidInvoke) {
    this.parameters = parameters;
    this.invalidInvoke = invalidInvoke;
  }

  private Collection<Class<?>> getProgramClasses() {
    return ImmutableList.of(I.class, A.class, C.class);
  }

  private Collection<byte[]> getProgramClassData() throws Exception {
    return ImmutableList.of(
        transformer(B.class)
            .setAccessFlags(
                B.class.getDeclaredMethod("m"),
                flags -> {
                  assert flags.isPublic();
                  flags.setStatic();
                })
            .transform(),
        transformer(TestClass.class)
            .transformMethodInsnInMethod(
                "main",
                (opcode, owner, name, descriptor, isInterface, continuation) -> {
                  if (invalidInvoke && opcode == Opcodes.INVOKEVIRTUAL) {
                    assertEquals("m", name);
                    continuation.visitMethodInsn(
                        opcode,
                        DescriptorUtils.getBinaryNameFromJavaType(C.class.getTypeName()),
                        name,
                        descriptor,
                        isInterface);
                  } else {
                    continuation.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                  }
                })
            .transform());
  }

  @Test
  public void testRuntime() throws Exception {
    checkResult(
        testForRuntime(parameters)
            .addProgramClasses(getProgramClasses())
            .addProgramClassFileData(getProgramClassData())
            .run(parameters.getRuntime(), TestClass.class),
        false);
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    checkResult(
        testForR8(parameters.getBackend())
            .addProgramClasses(getProgramClasses())
            .addProgramClassFileData(getProgramClassData())
            .addKeepAllClassesRule()
            .setMinApi(parameters)
            .compile()
            .run(parameters.getRuntime(), TestClass.class),
        true);
  }

  private void checkResult(TestRunResult<?> result, boolean isR8) {
    // Invalid invoke case is where the invoke-virtual targets C.m.
    if (invalidInvoke) {
      if (parameters.isDexRuntimeVersion(Version.V7_0_0)
          && parameters.canUseDefaultAndStaticInterfaceMethodsWhenDesugaring()
          && !isR8) {
        // The v7 VM incorrectly fails to throw.
        result.assertSuccessWithOutput(EXPECTED);
      } else {
        result.assertFailureWithErrorThatThrows(IncompatibleClassChangeError.class);
      }
      return;
    }

    if (isR8
        && parameters.isDexRuntime()
        && parameters.getDexRuntimeVersion().isNewerThan(Version.V6_0_1)
        && parameters.getDexRuntimeVersion().isOlderThan(Version.V12_0_0)
        && parameters.canUseDefaultAndStaticInterfaceMethodsWhenDesugaring()) {
      // TODO(b/182255398): This should be EXPECTED.
      result.assertSuccessWithOutput(EXPECTED_R8);
      return;
    }

    result.assertSuccessWithOutput(EXPECTED);
  }

  static class TestClass {

    public static void main(String[] args) {
      // Same as DefaultInterfaceMethodDesugaringWithStaticResolutionTest, but targets a class A.
      A /* or C */ a = new C();
      a.m();
    }
  }

  interface I {

    default void m() {
      System.out.println("I.m()");
    }
  }

  static class A implements I {}

  static class B extends A {

    public /* will be: public static */ void m() {
      System.out.println("B.m()");
    }
  }

  static class C extends B implements I {}
}
