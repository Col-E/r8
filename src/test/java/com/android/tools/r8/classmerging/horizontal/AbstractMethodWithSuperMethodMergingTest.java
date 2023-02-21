// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AbstractMethodWithSuperMethodMergingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addHorizontallyMergedClassesInspector(
            inspector -> inspector.assertIsCompleteMergeGroup(B1.class, B2.class))
        .enableInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccess();
  }

  static class Main {

    public static void main(String[] args) {
      boolean unknownButAlwaysTrue = System.currentTimeMillis() > 0;
      B1 c1 = unknownButAlwaysTrue ? new C1() : new C2();
      C3 c3 = unknownButAlwaysTrue ? new C3() : null;
      System.out.println(c1.foo());
      System.out.println(c3.foo());
    }
  }

  abstract static class A {

    public int foo() {
      return 0;
    }
  }

  @NoVerticalClassMerging
  abstract static class B1 extends A {

    @Override
    public abstract int foo();
  }

  @NoVerticalClassMerging
  abstract static class B2 extends A {}

  @NoHorizontalClassMerging
  static class C1 extends B1 {

    @Override
    @NeverInline
    public int foo() {
      return System.identityHashCode(this);
    }
  }

  @NoHorizontalClassMerging
  static class C2 extends B1 {

    @Override
    @NeverInline
    public int foo() {
      return System.identityHashCode(this);
    }
  }

  @NoHorizontalClassMerging
  static class C3 extends B2 {}
}
