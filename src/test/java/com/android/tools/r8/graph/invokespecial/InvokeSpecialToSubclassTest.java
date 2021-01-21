// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.invokespecial;

import static com.android.tools.r8.utils.DescriptorUtils.getBinaryNameFromJavaType;
import static org.junit.Assert.assertEquals;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvokeSpecialToSubclassTest extends TestBase {

  private static final Set<Class<?>> CLASSES_TO_TEST =
      ImmutableSet.of(C.class, EmptySubC.class, D.class);

  private final TestParameters parameters;
  private final Class<?> holder;

  @Parameters(name = "{0} class: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), CLASSES_TO_TEST);
  }

  public InvokeSpecialToSubclassTest(TestParameters parameters, Class<?> holder) {
    this.parameters = parameters;
    this.holder = holder;
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters.getRuntime(), parameters.getApiLevel())
        .addProgramClasses(EmptySubC.class, C.class, D.class, Main.class)
        .addProgramClassFileData(getClassBWithTransformedInvoked(holder))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Should not be called.");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(EmptySubC.class, C.class, D.class, Main.class)
        .addProgramClassFileData(getClassBWithTransformedInvoked(holder))
        .addKeepMainRule(Main.class)
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Should not be called.");
  }

  private byte[] getClassBWithTransformedInvoked(Class<?> holder) throws IOException {
    return transformer(B.class)
        .transformMethodInsnInMethod(
            "callPrint",
            (opcode, owner, name, descriptor, isInterface, continuation) -> {
              if (name.equals("replace")) {
                assertEquals(INVOKESPECIAL, opcode);
                assertEquals(getBinaryNameFromJavaType(B.class.getTypeName()), owner);
                continuation.visitMethodInsn(
                    opcode,
                    getBinaryNameFromJavaType(holder.getTypeName()),
                    "print",
                    descriptor,
                    isInterface);
              } else {
                continuation.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
              }
            })
        .transform();
  }

  public static class B {

    void print() {
      System.out.println("B");
    }

    void callPrint() {
      ((C) this).replace(); // Replaced by C, EmptySubC and D invoke-special to print.
    }
  }

  public static class C extends B {

    private void replace() {
      System.out.println("Should not be called.");
    }

    @Override
    void print() {
      System.out.println("C");
    }
  }

  public static class EmptySubC extends C {}

  public static class D extends EmptySubC {
    @Override
    void print() {
      System.out.println("C");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      new D().callPrint();
    }
  }
}
