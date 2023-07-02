// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.HorizontallyMergedClassesInspector;
import org.junit.Test;

public class UnresolvableMethodWithClassMergingTest extends HorizontalClassMergingTestBase {

  public UnresolvableMethodWithClassMergingTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, A.class, B.class)
        .addKeepMainRule(Main.class)
        .addDontWarn(Missing.class)
        .addHorizontallyMergedClassesInspector(
            HorizontallyMergedClassesInspector::assertNoClassesMerged)
        .setMinApi(parameters)
        .compile()
        .addRunClasspathFiles(buildOnDexRuntime(parameters, Missing.class))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A", "B");
  }

  public static class Main {
    public static void main(String[] args) {
      System.out.println(new A());
      Missing.print(new B());
    }
  }

  static class A {

    @Override
    public String toString() {
      return "A";
    }
  }

  static class B {

    @Override
    public String toString() {
      return "B";
    }
  }

  static class Missing {

    static void print(B b) {
      System.out.println(b);
    }
  }
}
