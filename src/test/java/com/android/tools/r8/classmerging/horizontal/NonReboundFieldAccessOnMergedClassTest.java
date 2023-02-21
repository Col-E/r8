// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.classmerging.horizontal.testclasses.NonReboundFieldAccessOnMergedClassTestClasses;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class NonReboundFieldAccessOnMergedClassTest extends HorizontalClassMergingTestBase {

  public NonReboundFieldAccessOnMergedClassTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addInnerClasses(NonReboundFieldAccessOnMergedClassTestClasses.class)
        .addKeepMainRule(Main.class)
        .addHorizontallyMergedClassesInspector(
            inspector -> inspector.assertMergedInto(D.class, C.class))
        .enableNeverClassInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  static class Main {

    public static void main(String[] args) {
      System.out.print(new C("Hello").greeting);
      System.out.println(new D(" world!").greeting);
    }
  }

  @NeverClassInline
  static class C extends NonReboundFieldAccessOnMergedClassTestClasses.B {

    public C(String greeting) {
      super(greeting);
    }
  }

  @NeverClassInline
  static class D extends NonReboundFieldAccessOnMergedClassTestClasses.B {

    public D(String greeting) {
      super(greeting);
    }
  }
}
