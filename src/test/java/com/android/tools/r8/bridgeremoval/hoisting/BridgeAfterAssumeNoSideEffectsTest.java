// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.bridgeremoval.hoisting;

import com.android.tools.r8.KeepConstantArguments;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoMethodStaticizing;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
// See b/295576241 for details.
public class BridgeAfterAssumeNoSideEffectsTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private static final String EXPECTED_OUTPUT = StringUtils.lines("Hello, world!");

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .addKeepRules(
            "-assumenosideeffects class "
                + TestClass.class.getTypeName()
                + " { void checkNotNull(java.lang.Object); }")
        .enableNoVerticalClassMergingAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableConstantArgumentAnnotations()
        .enableNoMethodStaticizingAnnotations()
        .enableInliningAnnotations()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  static class TestClass {
    static void checkNotNull(Object o) {
      if (o == null) throw new NullPointerException();
    }

    public static void main(String[] args) {
      System.out.print(new B().m("Hello,"));
      System.out.print(new C().m(" world!"));
      System.out.println();
    }
  }

  @NoVerticalClassMerging
  interface I {
    Object m(Object o);
  }

  @NoVerticalClassMerging
  @NoHorizontalClassMerging
  @NeverClassInline
  abstract static class A implements I {}

  @NoVerticalClassMerging
  @NoHorizontalClassMerging
  @NeverClassInline
  static class B extends A {
    @KeepConstantArguments
    @NeverInline
    @NoMethodStaticizing
    public Object m(Object o) {
      TestClass.checkNotNull(o);
      return o.toString();
    }
  }

  @NoVerticalClassMerging
  @NoHorizontalClassMerging
  @NeverClassInline
  static class C extends A {
    @KeepConstantArguments
    @NeverInline
    @NoMethodStaticizing
    public Object m(Object o) {
      return o;
    }
  }
}
