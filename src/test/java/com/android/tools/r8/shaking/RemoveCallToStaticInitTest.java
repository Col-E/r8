// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RemoveCallToStaticInitTest extends TestBase {

  private static final String EXPECTED = "Hello World!";
  private static final String R8_EXPECTED = "World!";

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(A.class, Main.class)
        .addProgramClassFileData(getBWithBridge())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(A.class, Main.class)
        .addProgramClassFileData(getBWithBridge())
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED)
        .inspect(
            inspector -> {
              ClassSubject clazz = inspector.clazz(B.class);
              assertThat(clazz, isPresent());
              assertThat(clazz.uniqueMethodWithOriginalName("foo"), isPresent());
            });
  }

  private byte[] getBWithBridge() throws Exception {
    return transformer(B.class)
        .setAccessFlags(B.class.getMethod("foo"), MethodAccessFlags::setBridge)
        .transform();
  }

  public static class A {

    @NeverInline
    public static void foo() {
      System.out.println("World!");
    }
  }

  public static class B extends A {

    static {
      System.out.print("Hello ");
    }

    @NeverInline
    public static /* bridge */ void foo() {
      A.foo();
    }
  }

  public static class Main {

    @NeverInline
    public static void test() {
      B.foo();
    }

    public static void main(String[] args) {
      test();
    }
  }
}
