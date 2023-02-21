// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import org.junit.Test;

public class VerticallyMergedClassDistinguishedByCheckCastTest
    extends HorizontalClassMergingTestBase {

  public VerticallyMergedClassDistinguishedByCheckCastTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("fail", "bar")
        .inspect(codeInspector -> {});
  }

  @NeverClassInline
  public static class Parent {
    @NeverInline
    public void bar() {
      System.out.println("bar");
    }
  }

  @NeverClassInline
  public static class A {
    @NeverInline
    public void foo() {
      System.out.println("foo");
    }
  }

  @NeverClassInline
  public static class B extends Parent {}

  public static class Main {
    @NeverInline
    public static void checkObject(Object o) {
      try {
        Parent b = (Parent) o;
        b.bar();
      } catch (ClassCastException ex) {
        System.out.println("fail");
      }
    }

    public static void main(String[] args) {
      A a = new A();
      B b = new B();
      checkObject(a);
      checkObject(b);
    }
  }
}
