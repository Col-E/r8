// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.transformers.ClassFileTransformer;
import com.android.tools.r8.utils.BooleanUtils;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.Opcodes;
// This is a reproduction of b/153042496 in a java-only setting.

@RunWith(Parameterized.class)
public class DefaultMethodWithAccessTest extends TestBase {

  private final TestParameters parameters;
  private final boolean implementI0I1;

  @Parameters(name = "{0}, implementI0I1: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  public DefaultMethodWithAccessTest(TestParameters parameters, boolean implementI0I1) {
    this.parameters = parameters;
    this.implementI0I1 = implementI0I1;
  }

  @Test
  public void testRuntime() throws ExecutionException, CompilationFailedException, IOException {
    testForRuntime(parameters)
        .addProgramClasses(I0.class, I1.class, Main.class, Impl.class)
        .addProgramClassFileData(transformI2AccessToInvokeSpecial())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello World!");
  }

  private byte[] transformI2AccessToInvokeSpecial() throws IOException {
    ClassFileTransformer classFileTransformer =
        transformer(I2.class)
            .transformMethodInsnInMethod(
                "access",
                (opcode, owner, name, descriptor, isInterface, continuation) -> {
                  continuation.visitMethodInsn(
                      name.equals("print") ? Opcodes.INVOKESPECIAL : opcode,
                      owner,
                      name,
                      descriptor,
                      isInterface);
                });
    if (implementI0I1) {
      classFileTransformer.setImplements(I0.class, I1.class);
    }
    return classFileTransformer.transform();
  }

  public interface I0 {
    void print();
  }

  public interface I1 {
    default void print() {
      System.out.println("Hello World!");
    }
  }

  public interface I2 extends /* I0, */ I1 {

    static void access(I2 i2) {
      /* invoke-special */ i2.print();
    }
  }

  public static class Impl implements I2 {}

  public static class Main {

    public static void main(String[] args) {
      testPrint(new Impl());
    }

    public static void testPrint(I2 i) {
      I2.access(i);
    }
  }
}
