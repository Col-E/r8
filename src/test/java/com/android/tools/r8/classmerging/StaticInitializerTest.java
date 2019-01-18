// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StaticInitializerTest extends TestBase {

  private final Backend backend;

  @Parameters(name = "Backend: {0}")
  public static Backend[] data() {
    return Backend.values();
  }

  public StaticInitializerTest(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void test() throws Exception {
    String expectedOutput = StringUtils.lines("In A.m()", "In B.<clinit>()", "In B.m()");

    if (backend == Backend.CF) {
      testForJvm().addTestClasspath().run(TestClass.class).assertSuccessWithOutput(expectedOutput);
    }

    testForR8(backend)
        .addInnerClasses(StaticInitializerTest.class)
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .run(TestClass.class)
        .assertSuccessWithOutput(expectedOutput);
  }

  static class TestClass {

    public static void main(String[] args) {
      A.m();
      B.m();
    }
  }

  // Cannot be merged into B because that would change the semantics due to <clinit>.
  static class A {

    @NeverInline
    public static void m() {
      System.out.println("In A.m()");
    }
  }

  static class B extends A {

    static {
      System.out.println("In B.<clinit>()");
    }

    @NeverInline
    public static void m() {
      System.out.println("In B.m()");
    }
  }
}
