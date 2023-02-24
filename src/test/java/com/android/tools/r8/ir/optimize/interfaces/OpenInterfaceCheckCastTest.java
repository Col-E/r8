// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.interfaces;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class OpenInterfaceCheckCastTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForRuntime(parameters)
        .addProgramClasses(getProgramClasses())
        .addProgramClassFileData(getTransformedMainClass())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(getExpectedOutputLines());
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .addProgramClasses(getProgramClasses())
        .addProgramClassFileData(getTransformedMainClass())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(getExpectedOutputLines());
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(getProgramClasses())
        .addProgramClassFileData(getTransformedMainClass())
        .addKeepMainRule(Main.class)
        .addOptionsModification(
            options -> options.getOpenClosedInterfacesOptions().suppressAllOpenInterfaces())
        .enableInliningAnnotations()
        // TODO(b/214496607): I should not be merged into A in the first place, since I is open.
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(getExpectedOutputLines());
  }

  private List<Class<?>> getProgramClasses() {
    return ImmutableList.of(I.class, A.class, B.class);
  }

  private byte[] getTransformedMainClass() throws IOException {
    return transformer(Main.class)
        .transformTypeInsnInMethod(
            "asI",
            (opcode, type, visitor) -> {
              assertEquals(opcode, Opcodes.CHECKCAST);
              assertEquals(type, binaryName(A.class));
              visitor.visitTypeInsn(opcode, binaryName(I.class));
            })
        .transformTypeInsnInMethod(
            "getB",
            (opcode, type, visitor) -> {
              assertEquals(opcode, Opcodes.NEW);
              assertEquals(type, binaryName(A.class));
              visitor.visitTypeInsn(opcode, binaryName(B.class));
            })
        .transformMethodInsnInMethod(
            "getB",
            (opcode, owner, name, descriptor, isInterface, visitor) -> {
              assertEquals(opcode, Opcodes.INVOKESPECIAL);
              assertEquals(owner, binaryName(A.class));
              assertEquals(name, "<init>");
              visitor.visitMethodInsn(opcode, binaryName(B.class), name, descriptor, isInterface);
            })
        .transform();
  }

  private List<String> getExpectedOutputLines() {
    if (parameters.isDexRuntime()
        && parameters
            .getDexRuntimeVersion()
            .isEqualToOneOf(Version.V5_1_1, Version.V8_1_0, Version.V9_0_0, Version.DEFAULT)) {
      return ImmutableList.of("OK", "OK");
    }
    return ImmutableList.of("OK", "FAIL");
  }

  static class Main {

    public static void main(String[] args) {
      try {
        asI(getA());
        System.out.println("OK");
      } catch (ClassCastException e) {
        System.out.println("FAIL");
      }
      try {
        asI(getB());
        System.out.println("OK");
      } catch (ClassCastException e) {
        System.out.println("FAIL");
      }
    }

    @NeverInline
    static I asI(I i) {
      return (A) i; // transformed into a cast to I.
    }

    static I getA() {
      return new A();
    }

    static I getB() {
      return new A(); // transformed into new B().
    }
  }

  @NoVerticalClassMerging
  interface I {}

  static class A implements I {}

  static class B {}
}
