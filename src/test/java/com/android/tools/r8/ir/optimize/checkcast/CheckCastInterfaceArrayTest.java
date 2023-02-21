// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.checkcast;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class CheckCastInterfaceArrayTest extends TestBase {

  private static final String EXPECTED = StringUtils.lines("A", "B");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  public CheckCastInterfaceArrayTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testJvmAndD8() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(I.class, A.class, B.class, TestClass.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    Assume.assumeTrue("b/129410384", parameters.isCfRuntime());
    testForR8(parameters.getBackend())
        .addInnerClasses(CheckCastInterfaceArrayTest.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .addDontObfuscate()
        .noTreeShaking()
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testJvmAndD8Throwing() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(I.class, A.class, B.class, TestClassError.class)
        .run(parameters.getRuntime(), TestClassError.class)
        .applyIf(
            parameters.isDexRuntime(),
            result -> result.assertFailureWithErrorThatThrows(VerifyError.class),
            result -> result.assertSuccessWithOutput(EXPECTED));
  }

  interface I {
    int id();
  }

  enum A implements I {
    A;
    static final int id = 0;

    @Override
    public int id() {
      return id;
    }
  }

  enum B implements I {
    B;
    static final int id = 1;

    @Override
    public int id() {
      return id;
    }
  }

  static class TestClass {

    public static I[] get(I kind) {
      // Work around the ART bug by inserting an explicit check cast (fortunately javac keeps it).
      return (I[]) (kind.id() == A.id ? A.values() : B.values());
    }

    public static void main(String[] args) {
      for (I i : get(A.A)) {
        System.out.println(i);
      }
      for (I i : get(B.B)) {
        System.out.println(i);
      }
    }
  }

  static class TestClassError {

    public static I[] get(I kind) {
      // Work around the ART bug by inserting an explicit check cast (fortunately javac keeps it).
      return (kind.id() == A.id ? A.values() : B.values());
    }

    public static void main(String[] args) {
      for (I i : get(A.A)) {
        System.out.println(i);
      }
      for (I i : get(B.B)) {
        System.out.println(i);
      }
    }
  }
}
