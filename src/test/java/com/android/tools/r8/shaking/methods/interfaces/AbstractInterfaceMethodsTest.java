// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.methods.interfaces;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * This tests is showing the issue filed in b/143590191. The expectations for the test should
 * reflect the decisions to keep the interface method or not in the super interface.
 */
@RunWith(Parameterized.class)
public class AbstractInterfaceMethodsTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public AbstractInterfaceMethodsTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testSingleInheritanceProguard()
      throws CompilationFailedException, IOException, ExecutionException {
    assumeTrue(parameters.isCfRuntime());
    testForProguard()
        .addProgramClasses(I.class, J.class)
        .setMinApi(parameters.getApiLevel())
        .addKeepMethodRules(J.class, "void foo()")
        .addKeepRules("-dontwarn")
        .compile()
        .inspect(
            inspector -> {
              ClassSubject clazz = inspector.clazz(J.class);
              assertThat(clazz, isPresent());
              assertThat(clazz.uniqueMethodWithOriginalName("foo"), not(isPresent()));
              assertThat(inspector.clazz(I.class), not(isPresent()));
            });
  }

  @Test
  public void testSingleInheritanceR8()
      throws CompilationFailedException, IOException, ExecutionException {
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(I.class, J.class)
            .setMinApi(parameters.getApiLevel())
            .addKeepMethodRules(J.class, "void foo()")
            .compile();
    testForRuntime(parameters)
        .addProgramClasses(A.class, Main.class)
        .addRunClasspathFiles(compileResult.writeToZip())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello World!");
  }

  public interface I {
    void foo();
  }

  public interface J extends I {}

  public static class A implements J {

    @Override
    public void foo() {
      System.out.println("Hello World!");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      ((J) new A()).foo();
    }
  }
}
