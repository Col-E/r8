// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.proto;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.Reference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ProtoNormalizationDuplicateMethodTest extends TestBase {

  private final String[] EXPECTED = new String[] {"Base::foo-7Calling Sub::foo8"};

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(Main.class)
        .addKeepMethodRules(
            Reference.methodFromMethod(
                B.class.getDeclaredMethod("foo$1", int.class, int.class, String.class)))
        .enableInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @NoHorizontalClassMerging
  public static class B {

    @NeverInline
    public void foo(int i, String s, int j) {
      System.out.println("Base::foo-" + i + s + j);
    }

    @NeverInline
    public void foo$1(int i, int j, String s) {
      throw new RuntimeException("Should never be called: " + i + j + s);
    }
  }

  @NoHorizontalClassMerging
  public static class A {

    @NeverInline
    public void foo(String s, int i, int j) {
      System.out.println("B-" + i + s + j);
    }
  }

  public static class Main {

    public static void main(String[] args) {
      if (System.currentTimeMillis() == 0) {
        new A().foo("Calling Sub::foo", 3, 4);
        new B().foo$1(5, 6, "Calling Sub::foo");
      }
      new B().foo(7, "Calling Sub::foo", 8);
    }
  }
}
