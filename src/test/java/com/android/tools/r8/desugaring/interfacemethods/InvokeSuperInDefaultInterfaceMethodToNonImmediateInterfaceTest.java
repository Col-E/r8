// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugaring.interfacemethods;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class InvokeSuperInDefaultInterfaceMethodToNonImmediateInterfaceTest extends TestBase {

  private final TestParameters parameters;
  private final boolean includeInterfaceMethodOnJ;

  // Note that the expected output is independent of the presence of J.m().
  private static final String EXPECTED = StringUtils.lines("I.m()", "JImpl.m()");

  @Parameterized.Parameters(name = "{0}, J.m(): {1}")
  public static List<Object[]> data() {
    return buildParameters(
        TestParameters.builder()
            .withCfRuntimes()
            .enableApiLevelsForCf()
            .withDexRuntimes()
            .withApiLevel(AndroidApiLevel.B)
            .build(),
        BooleanUtils.values());
  }

  public InvokeSuperInDefaultInterfaceMethodToNonImmediateInterfaceTest(
      TestParameters parameters, boolean includeInterfaceMethodOnJ) {
    this.parameters = parameters;
    this.includeInterfaceMethodOnJ = includeInterfaceMethodOnJ;
  }

  @Test
  public void testJvm() throws Exception {
    // The rewritten input is invalid on JVM.
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClasses(getClasses())
        .addProgramClassFileData(getTransformedClasses())
        .run(parameters.getRuntime(), TestClass.class)
        .assertFailureWithErrorThatThrows(VerifyError.class);
  }

  @Test
  public void testD8() throws Exception {
    // Notice that desugaring will map out of the invalid invoke.
    testForD8(parameters.getBackend())
        .addProgramClasses(getClasses())
        .addProgramClassFileData(getTransformedClasses())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    // Notice that desugaring will map out of the invalid invoke.
    testForR8(parameters.getBackend())
        .addProgramClasses(getClasses())
        .addProgramClassFileData(getTransformedClasses())
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  private List<Class<?>> getClasses() {
    return ImmutableList.of(TestClass.class, I.class);
  }

  private List<byte[]> getTransformedClasses() throws IOException {
    return ImmutableList.of(
        transformer(JImpl.class)
            .transformMethodInsnInMethod(
                "m",
                (opcode, owner, name, descriptor, isInterface, visitor) -> {
                  if (opcode == Opcodes.INVOKESPECIAL) {
                    assertEquals(owner, binaryName(J.class));
                    owner = binaryName(I.class);
                  }
                  visitor.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                })
            .transform(),
        transformer(J.class)
            .removeMethods(
                (access, name, descriptor, signature, exceptions) ->
                    !includeInterfaceMethodOnJ && name.equals("m"))
            .transform());
  }

  static class TestClass {

    public static void main(String[] args) {
      new JImpl().m();
    }
  }

  interface I {

    default void m() {
      System.out.println("I.m()");
    }
  }

  interface J extends I {

    @Override
    default void m() {
      I.super.m();
      System.out.println("J.m()");
    }
  }

  static class JImpl implements J {

    @Override
    public void m() {
      J.super.m(); // Will be rewritten to non-immediate interface: I.super.m();
      System.out.println("JImpl.m()");
    }
  }
}
