// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class PrivateMethodsInInterfaceTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public PrivateMethodsInInterfaceTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRuntime()
      throws NoSuchMethodException, IOException, CompilationFailedException, ExecutionException {
    testForRuntime(parameters)
        .addProgramClasses(SubI.class, Impl.class, Main.class)
        .addProgramClassFileData(transformIToPrivate())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello World!", "Hello World!", "Hello World!");
  }

  private byte[] transformIToPrivate() throws NoSuchMethodException, IOException {
    return transformer(I.class)
        .setPrivate(I.class.getDeclaredMethod("bar"))
        .setPrivate(I.class.getDeclaredMethod("baz", I.class))
        .transformMethodInsnInMethod(
            "foo",
            ((opcode, owner, name, descriptor, isInterface, continuation) -> {
              continuation.visitMethodInsn(
                  name.equals("bar") ? Opcodes.INVOKESPECIAL : opcode,
                  owner,
                  name,
                  descriptor,
                  isInterface);
            }))
        .transformMethodInsnInMethod(
            "baz",
            ((opcode, owner, name, descriptor, isInterface, continuation) -> {
              continuation.visitMethodInsn(
                  name.equals("bar") ? Opcodes.INVOKESPECIAL : opcode,
                  owner,
                  name,
                  descriptor,
                  isInterface);
            }))
        .transform();
  }

  public interface I {

    default void foo() {
      bar();
      I.qux(this);
    }

    /* private */ default void bar() {
      System.out.println("Hello World!");
    }

    /* private */ static void baz(I i) {
      i.bar();
    }

    static void qux(I i) {
      baz(i);
    }
  }

  public interface SubI extends I {}

  public static class Impl implements SubI {}

  public static class Main {

    public static void main(String[] args) {
      Impl impl = new Impl();
      impl.foo();
      I.qux(impl);
    }
  }
}
