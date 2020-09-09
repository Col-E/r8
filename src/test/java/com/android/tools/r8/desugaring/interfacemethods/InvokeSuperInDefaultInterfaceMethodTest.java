// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugaring.interfacemethods;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;

public class InvokeSuperInDefaultInterfaceMethodTest extends TestBase {

  @Test
  public void test() throws Exception {
    String expectedOutput = StringUtils.lines("I.m()", "J.m()", "JImpl.m()", "I.m()", "KImpl.m()");

    testForJvm().addTestClasspath().run(TestClass.class).assertSuccessWithOutput(expectedOutput);

    testForR8(Backend.DEX)
        .addInnerClasses(InvokeSuperInDefaultInterfaceMethodTest.class)
        .addKeepMainRule(TestClass.class)
        .enableNeverClassInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(AndroidApiLevel.M)
        .run(TestClass.class)
        .assertSuccessWithOutput(expectedOutput);
  }

  static class TestClass {

    public static void main(String[] args) {
      new JImpl().m();
      new KImpl().m();
    }
  }

  @NoVerticalClassMerging
  interface I {

    default void m() {
      System.out.println("I.m()");
    }
  }

  @NoVerticalClassMerging
  interface J extends I {

    @Override
    default void m() {
      I.super.m();
      System.out.println("J.m()");
    }
  }

  @NoVerticalClassMerging
  interface K extends I {

    // Intentionally does not override I.m().
  }

  @NeverClassInline
  static class JImpl implements J {

    @Override
    public void m() {
      J.super.m();
      System.out.println("JImpl.m()");
    }
  }

  @NeverClassInline
  static class KImpl implements K {

    @Override
    public void m() {
      K.super.m();
      System.out.println("KImpl.m()");
    }
  }
}
