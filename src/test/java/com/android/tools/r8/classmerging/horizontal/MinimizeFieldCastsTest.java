// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestParameters;
import org.junit.Test;

public class MinimizeFieldCastsTest extends HorizontalClassMergingTestBase {

  public MinimizeFieldCastsTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters)
        .addHorizontallyMergedClassesInspector(
            inspector ->
                // Two merge groups are expected since we attempt to merge classes in a way that
                // avoids merging fields with different types unless strictly required for merging.
                inspector.assertMergedInto(B.class, A.class).assertMergedInto(D.class, C.class))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Foo", "Foo", "Bar", "Bar");
  }

  @NeverClassInline
  public static class A {
    FooGreeter greeter;

    A(FooGreeter greeter) {
      this.greeter = greeter;
    }

    void greet() {
      greeter.greet();
    }
  }

  @NeverClassInline
  public static class B {
    FooGreeter greeter;

    B(FooGreeter greeter) {
      this.greeter = greeter;
    }

    void greet() {
      greeter.greet();
    }
  }

  @NeverClassInline
  public static class C {
    BarGreeter greeter;

    C(BarGreeter greeter) {
      this.greeter = greeter;
    }

    void greet() {
      greeter.greet();
    }
  }

  @NeverClassInline
  public static class D {
    BarGreeter greeter;

    D(BarGreeter greeter) {
      this.greeter = greeter;
    }

    void greet() {
      greeter.greet();
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  static class FooGreeter {

    @NeverInline
    void greet() {
      System.out.println("Foo");
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  static class BarGreeter {

    @NeverInline
    void greet() {
      System.out.println("Bar");
    }
  }

  static class Main {
    public static void main(String[] args) {
      new A(new FooGreeter()).greet();
      new B(new FooGreeter()).greet();
      new C(new BarGreeter()).greet();
      new D(new BarGreeter()).greet();
    }
  }
}
