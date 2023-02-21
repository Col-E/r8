// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.invokespecial;

import static com.android.tools.r8.utils.DescriptorUtils.getBinaryNameFromJavaType;
import static org.junit.Assert.assertEquals;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvokeSpecialToSuperclassTest extends TestBase {

  private static final Set<Class<?>> CLASSES_TO_TEST =
      ImmutableSet.of(S.class, A.class, EmptySubA.class);

  private final TestParameters parameters;
  private final Class<?> holder;

  @Parameters(name = "{0} class: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), CLASSES_TO_TEST);
  }

  public InvokeSpecialToSuperclassTest(TestParameters parameters, Class<?> holder) {
    this.parameters = parameters;
    this.holder = holder;
  }

  public String getExpectedResult(boolean r8) {
    if (holder == S.class
        && parameters.isDexRuntime()
        && !r8
        && parameters.getDexRuntimeVersion().isNewerThanOrEqual(ToolHelper.DexVm.Version.V5_1_1)
        && parameters.getDexRuntimeVersion().isOlderThanOrEqual(ToolHelper.DexVm.Version.V6_0_1)) {
      // TODO(b/175285016): Should be "A".
      // On Android 5-6, the result for S.class is "S" instead of "A" with D8 compilation.
      return "S";
    }
    return "A";
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters.getRuntime(), parameters.getApiLevel())
        .addProgramClasses(S.class, A.class, EmptySubA.class, Main.class)
        .addProgramClassFileData(getClassBWithTransformedInvoked(holder))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(getExpectedResult(false));
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(S.class, A.class, EmptySubA.class, Main.class)
        .addProgramClassFileData(getClassBWithTransformedInvoked(holder))
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(getExpectedResult(true));
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

  public static class S {

    void print() {
      System.out.println("S");
    }
  }

  public static class A extends S {

    void print() {
      System.out.println("A");
    }
  }

  public static class EmptySubA extends A {}

  public static class B extends EmptySubA {

    void print() {
      System.out.println("B");
    }

    private void replace() {
      System.out.println("Should not be called.");
    }

    void callPrint() {
      replace(); // Replaced by S, A, EmptySubA invoke-special to print.
    }
  }

  public static class Main {

    public static void main(String[] args) {
      new B().callPrint();
    }
  }
}
