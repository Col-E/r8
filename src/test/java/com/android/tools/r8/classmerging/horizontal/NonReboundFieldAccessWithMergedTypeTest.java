// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.classmerging.horizontal.testclasses.NonReboundFieldAccessWithMergedTypeTestClasses;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class NonReboundFieldAccessWithMergedTypeTest extends HorizontalClassMergingTestBase {

  public NonReboundFieldAccessWithMergedTypeTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addInnerClasses(NonReboundFieldAccessWithMergedTypeTestClasses.class)
        .addKeepMainRule(Main.class)
        .addHorizontallyMergedClassesInspector(
            inspector -> inspector.assertMergedInto(WorldGreeting.class, HelloGreeting.class))
        .enableNeverClassInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  static class Main {

    public static void main(String[] args) {
      System.out.print(new HelloGreeting());
      System.out.println(new C(new WorldGreeting()).greeting);
    }
  }

  @NeverClassInline
  static class C extends NonReboundFieldAccessWithMergedTypeTestClasses.B {

    public C(WorldGreeting greeting) {
      super(greeting);
    }
  }

  public static class HelloGreeting {

    @Override
    public String toString() {
      return "Hello";
    }
  }

  public static class WorldGreeting {

    @Override
    public String toString() {
      return " world!";
    }
  }
}
