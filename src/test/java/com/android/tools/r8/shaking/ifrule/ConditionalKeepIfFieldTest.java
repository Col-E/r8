// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.ifrule;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.ProguardVersion;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestShrinkerBuilder;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ConditionalKeepIfFieldTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultCfRuntime().build();
  }

  private TestParameters parameters;

  public ConditionalKeepIfFieldTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    run(testForR8(parameters.getBackend()));
  }

  @Test
  public void testPG() throws Exception {
    assertTrue(parameters.isCfRuntime());
    run(testForProguard(ProguardVersion.getLatest()).addDontWarn(getClass()));
  }

  private void run(TestShrinkerBuilder<?, ?, ?, ?, ?> builder)
      throws IOException, ExecutionException, CompilationFailedException {
    builder
        .addProgramClasses(A.class, B.class, TestClass.class)
        .addKeepRules("-if class * { *** *; } -keep class <1> { *; }")
        .addKeepMainRule(TestClass.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("42", "0")
        .inspect(
            inspector -> {
              // Check that the above rule only ends up keeping the class with an actual field.
              assertThat(inspector.clazz(A.class), isAbsent());
              assertThat(inspector.clazz(B.class), isPresent());
            });
  }

  static class A {

    public static void foo() {
      System.out.println(42);
    }
  }

  static class B {
    public static int x;

    public static void foo() {
      System.out.println("" + x);
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      A.foo();
      B.x = args.length;
      B.foo();
    }
  }
}
