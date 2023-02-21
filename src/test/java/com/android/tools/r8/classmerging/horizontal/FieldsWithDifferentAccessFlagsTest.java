/*
 *  // Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
 *  // for details. All rights reserved. Use of this source code is governed by a
 *  // BSD-style license that can be found in the LICENSE file.
 */

package com.android.tools.r8.classmerging.horizontal;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.HorizontallyMergedClassesInspector;
import org.junit.Test;

public class FieldsWithDifferentAccessFlagsTest extends HorizontalClassMergingTestBase {

  public FieldsWithDifferentAccessFlagsTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addHorizontallyMergedClassesInspector(
            HorizontallyMergedClassesInspector::assertNoClassesMerged)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A", "B", "C");
  }

  @NeverClassInline
  public static class A {
    public volatile String msg;

    public A(String msg) {
      this.msg = msg;
    }

    @NeverInline
    public void foo() {
      System.out.println(msg);
    }
  }

  @NeverClassInline
  public static class B {
    public transient String msg;

    public B(String msg) {
      this.msg = msg;
    }

    @NeverInline
    public void foo() {
      System.out.println(msg);
    }
  }

  @NeverClassInline
  public static class C {
    public String msg;

    public C(String msg) {
      this.msg = msg;
    }

    @NeverInline
    public void foo() {
      System.out.println(msg);
    }
  }

  public static class Main {
    public static void main(String[] args) {
      new A(System.currentTimeMillis() > 0 ? "A" : null).foo();
      new B(System.currentTimeMillis() > 0 ? "B" : null).foo();
      new C(System.currentTimeMillis() > 0 ? "C" : null).foo();
    }
  }
}
