// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestParameters;
import org.junit.Test;

public class GenericStaticFieldTest extends HorizontalClassMergingTestBase {
  public GenericStaticFieldTest(TestParameters parameters, boolean enableHorizontalClassMerging) {
    super(parameters, enableHorizontalClassMerging);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepRules("-keepattributes Signatures")
        .addOptionsModification(
            options -> options.enableHorizontalClassMerging = enableHorizontalClassMerging)
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        // .addHorizontallyMergedClassesInspectorIf(
        //    enableHorizontalClassMerging, inspector ->
        // inspector.assertMergedInto(EmptyClassTest.B.class, EmptyClassTest.A.class))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("false", "false")
        .inspect(codeInspector -> {});
  }

  @NeverClassInline
  public static class A<Z extends Object> {
    public A(Z z) {
      if (System.currentTimeMillis() < 0) {
        z.toString();
      }
    }
  }

  @NeverClassInline
  public static class B {}

  @NeverClassInline
  public static class Foo {
    public static A<Object> field;
    public static A<B> field2;

    public Foo() {
      field = new A<Object>(new Object());
    }
  }

  @NeverClassInline
  public static class Foo2 {
    public static A<Object> field;

    public Foo2() {
      field = new A<Object>(new Object());
    }
  }

  public static class Main {
    public static void main(String[] args) {
      Foo foo = new Foo();
      Foo2 foo2 = new Foo2();
      System.out.println(foo.field.equals(System.out));
      System.out.println(foo2.field.equals(System.out));
    }
  }
}
