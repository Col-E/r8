// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.methods.interfaces;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InterfaceMethodDefinedInLibraryTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public InterfaceMethodDefinedInLibraryTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testMethodInLibrary()
      throws CompilationFailedException, IOException, ExecutionException {
    R8TestCompileResult intermediateResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(A.class)
            .addClasspathClasses(I.class)
            .addKeepMethodRules(A.class, "void foo()", "void <init>()")
            .setMinApi(parameters)
            .compile();
    testForRuntime(parameters)
        .addProgramClasses(Main.class)
        .addRunClasspathFiles(intermediateResult.writeToZip())
        .addRunClasspathFiles(
            parameters.isDexRuntime()
                ? testForD8()
                    .addProgramClasses(I.class)
                    .setMinApi(parameters)
                    .compile()
                    .writeToZip()
                : ToolHelper.getClassPathForTests())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello World!");
  }

  public interface I {

    default void foo() {
      System.out.println("Hello World!");
    }
  }

  public static class A implements I {}

  public static class Main {

    public static void main(String[] args) {
      new A().foo();
    }
  }
}
