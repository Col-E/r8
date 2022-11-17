// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.proto;

import com.android.tools.r8.NeverInline;
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
public class ProtoNormalizationIntroduceCollisionTest extends TestBase {

  private final String[] EXPECTED = new String[] {"Base::foo-42Calling B::foo1337"};
  private final String[] R8_EXPECTED = new String[] {"Sub::foo-Calling B::foo421337"};

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(Main.class, Base.class, Sub.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(Main.class)
        .addKeepClassAndMembersRules(Base.class)
        .enableInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/258720808): We should produce the expected result.
        .assertSuccessWithOutputLines(R8_EXPECTED);
  }

  public static class Base {

    @NeverInline
    public void foo(int i, int j, String s) {
      System.out.println("Base::foo-" + i + s + j);
    }
  }

  @NoVerticalClassMerging
  public static class Sub extends Base {

    @NeverInline
    public void foo(String s, int i, int j) {
      System.out.println("Sub::foo-" + s + i + j);
    }
  }

  public static class Main {

    public static void main(String[] args) {
      if (System.currentTimeMillis() == 0) {
        callFoo(new Base());
        new Sub().foo("Calling Sub::foo", 1, 2);
      }
      callFoo(new Sub());
    }

    public static void callFoo(Base b) {
      b.foo(42, 1337, "Calling B::foo");
    }
  }
}
