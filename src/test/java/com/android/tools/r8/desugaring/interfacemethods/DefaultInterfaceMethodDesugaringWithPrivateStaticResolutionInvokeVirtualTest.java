// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugaring.interfacemethods;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.ToolHelper.DexVm;
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
public class DefaultInterfaceMethodDesugaringWithPrivateStaticResolutionInvokeVirtualTest
    extends TestBase {

  private static final String EXPECTED = StringUtils.lines("I.m()");

  private final TestParameters parameters;
  private final boolean invalidInvoke;

  @Parameterized.Parameters(name = "{0}, invalid:{1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  public DefaultInterfaceMethodDesugaringWithPrivateStaticResolutionInvokeVirtualTest(
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
                  flags.unsetPublic();
                  flags.setPrivate();
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
      if (!isR8) {
        // Up to 4.4 the exception for targeting a private static was ICCE.
        if (isDexOlderThanOrEqual(Version.V4_4_4)) {
          result.assertFailureWithErrorThatThrows(IncompatibleClassChangeError.class);
          return;
        }
        // Then up to 6.0 the runtime just ignores privates leading to incorrectly hitting I.m
        if (isDexOlderThanOrEqual(Version.V6_0_1)) {
          result.assertSuccessWithOutput(EXPECTED);
          return;
        }
      }
      // The expected behavior is IAE since the resolved method is private.
      result.assertFailureWithErrorThatThrows(IllegalAccessError.class);
      return;
    }

    // The non-invalid case is where the invoke-virtual targets A.m.

    // In the successful case ART since 6.0 incorrectly throws IAE due to the private override.
    if (unexpectedArtFailure()) {
      result.assertFailureWithErrorThatThrows(IllegalAccessError.class);
      return;
    }

    // The expected behavior is that the resolution of A.m will resolve and hit I.m.
    result.assertSuccessWithOutput(EXPECTED);
  }

  private boolean isDexOlderThanOrEqual(Version version) {
    return parameters.isDexRuntime()
        && parameters.getRuntime().asDex().getVm().getVersion().isOlderThanOrEqual(version);
  }

  private boolean unexpectedArtFailure() {
    return parameters.isDexRuntime()
        && parameters.getRuntime().asDex().getVm().isNewerThan(DexVm.ART_6_0_1_HOST)
        && parameters.getRuntime().asDex().getVm().isOlderThan(DexVm.ART_12_0_0_HOST);
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

    public /* will be: private static */ void m() {
      System.out.println("B.m()");
    }
  }

  static class C extends B implements I {}
}
