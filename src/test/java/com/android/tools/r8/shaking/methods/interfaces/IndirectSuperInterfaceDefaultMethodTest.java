// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.methods.interfaces;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IndirectSuperInterfaceDefaultMethodTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntimes().build();
  }

  public IndirectSuperInterfaceDefaultMethodTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testInvokeSpecial()
      throws CompilationFailedException, IOException, ExecutionException {
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(I.class, J.class, K.class)
            .addKeepClassRules(J.class)
            .addKeepClassAndMembersRules(I.class)
            .addKeepMethodRules(K.class, "void foo()")
            .compile();
    testForRuntime(parameters)
        .addProgramClasses(A.class, Main.class)
        .addRunClasspathFiles(compileResult.writeToZip())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello World!");
  }

  public interface I {
    default void foo() {
      System.out.println("Hello World!");
    }
  }

  public interface J extends I {}

  public interface K extends J {}

  public static class A implements K {}

  public static class Main {

    public static void main(String[] args) {
      ((K) new A()).foo();
    }
  }
}
