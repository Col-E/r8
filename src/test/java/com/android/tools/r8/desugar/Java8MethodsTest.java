// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar;

import com.android.tools.r8.TestBase;
import org.junit.Before;
import org.junit.Test;

public class Java8MethodsTest extends TestBase {
  static String expectedOutput = "";

  @Before
  public void testJvm() throws Exception {
    expectedOutput = testForJvm()
        .addTestClasspath()
        .run(Java8Methods.class).getStdOut();
  }

  @Test
  public void testD8() throws Exception {
    testForD8()
        .addProgramClasses(Java8Methods.class)
        .run(Java8Methods.class)
        .assertSuccessWithOutput(expectedOutput);
  }

  static class Java8Methods {
    public static void main(String[] args) {
      System.out.println(Integer.hashCode(42));
      System.out.println(Integer.max(42, 3));
      System.out.println(Double.hashCode(42.0));
      System.out.println(Double.max(42.0, 43.0));
    }
  }
}
