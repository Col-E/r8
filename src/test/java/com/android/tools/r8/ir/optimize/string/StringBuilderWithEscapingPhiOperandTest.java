// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.string;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** This is a reproduction of b/280958704. */
@RunWith(Parameterized.class)
public class StringBuilderWithEscapingPhiOperandTest extends TestBase {

  private final String EXPECTED = "(Hello World)";

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClasses(Main.class)
        .run(parameters.getRuntime(), Main.class, "Hello World")
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testD8Debug() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters.getBackend())
        .addInnerClasses(getClass())
        .debug()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class, "Hello World")
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testD8Release() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters.getBackend())
        .addInnerClasses(getClass())
        .release()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class, "Hello World")
        // TODO(b/280958704): This should have same output as D8 debug/JVM.
        .assertSuccessWithOutputLines("(Hello World");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject clazz = inspector.clazz(Main.class);
              assertThat(clazz, isPresent());
              assertThat(clazz.uniqueMethodWithOriginalName("storeInField"), isAbsent());
            })
        .run(parameters.getRuntime(), Main.class, "Hello World")
        // TODO(b/280958704): This should have same output as D8 debug/JVM.
        .assertSuccessWithOutputLines("(Hello World");
  }

  static class Main {

    static StringBuilder escape;

    public static void main(String[] args) {
      addString(args[0]);
      System.out.println(escape);
    }

    @NeverInline
    private static void addString(CharSequence sequence) {
      StringBuilder sb = System.currentTimeMillis() > 0 ? new StringBuilder() : null;
      if (sb != null) {
        sb = storeInField(sb);
      }
      sb.append("(").append(sequence).append(")");
    }

    // Inlining this method creates an indirection through an assume node in a separate block.
    // Adding the store inside the if above will have the assume in the same block.
    public static StringBuilder storeInField(StringBuilder builder) {
      return escape = builder;
    }
  }
}
