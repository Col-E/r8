// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.methods.interfaces;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.CompilationFailedException;
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
public class SubTypeOverridesInterfaceMethodTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public SubTypeOverridesInterfaceMethodTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testKeepInterfaceMethodOnImplementingType()
      throws CompilationFailedException, IOException, ExecutionException {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, I.class, A.class, B.class)
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(Main.class)
        .addKeepMethodRules(A.class, "void <init>()", "void foo()")
        .addKeepClassRules(B.class)
        .run(parameters.getRuntime(), Main.class, B.class.getTypeName(), "foo")
        .assertSuccessWithOutputLines("Hello World!")
        .inspect(
            inspector -> {
              ClassSubject clazz = inspector.clazz(A.class);
              assertThat(clazz, isPresent());
              assertThat(clazz.uniqueMethodWithOriginalName("foo"), isPresent());
            });
  }

  public interface I {
    void foo();
  }

  public abstract static class A implements I {}

  public static class B extends A {

    @Override
    public void foo() {
      System.out.println("Hello World!");
    }
  }

  public static class Main {

    public static void main(String[] args) throws Exception {
      Object o = Class.forName(args[0]).getDeclaredConstructor().newInstance();
      o.getClass().getMethod(args[1]).invoke(o);
    }
  }
}
