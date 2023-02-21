// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.membervaluepropagation;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.AccessFlags;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * Tests the handling of final fields that are assigned more than once.
 *
 * <p>According to the JVM specification, fields that are declared final must never be assigned to
 * after object construction, but the specification does not require that the field is assigned at
 * most once.
 */
@RunWith(Parameterized.class)
public class FinalFieldWithMultipleWritesTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(Main.class)
        .addProgramClassFileData(getTransformedClass())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("0", "1", "2", "2");
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class)
        .addProgramClassFileData(getTransformedClass())
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("0", "1", "2", "2");
  }

  private static byte[] getTransformedClass() throws IOException, NoSuchFieldException {
    return transformer(A.class)
        .setAccessFlags(A.class.getDeclaredField("f"), AccessFlags::setFinal)
        .transform();
  }

  static class Main {

    public static void main(String[] args) {
      A a = new A();
      System.out.println(a.f);
    }
  }

  static class A {

    /* final */ int f;

    A() {
      System.out.println(this);
      f = 1;
      System.out.println(this);
      f = 2;
      System.out.println(this);
    }

    @Override
    public String toString() {
      return Integer.toString(f);
    }
  }
}
