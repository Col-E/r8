// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class VirtualMethodMergingOfPublicizedMethodsTest extends HorizontalClassMergingTestBase {

  public VirtualMethodMergingOfPublicizedMethodsTest(
      TestParameters parameters, boolean enableHorizontalClassMerging) {
    super(parameters, enableHorizontalClassMerging);
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .addOptionsModification(
            options -> options.enableHorizontalClassMerging = enableHorizontalClassMerging)
        .allowAccessModification()
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(
            "A.privateAndPrivate()",
            "B.privateAndPrivate()",
            "A.privateAndPackagePrivate()",
            "B.privateAndPackagePrivate()",
            "A.privateAndPublic()",
            "B.privateAndPublic()",
            "A.packagePrivateAndPrivate()",
            "B.packagePrivateAndPrivate()",
            "A.packagePrivateAndPackagePrivate()",
            "B.packagePrivateAndPackagePrivate()",
            "A.packagePrivateAndPublic()",
            "B.packagePrivateAndPublic()",
            "A.publicAndPrivate()",
            "B.publicAndPrivate()",
            "A.publicAndPackagePrivate()",
            "B.publicAndPackagePrivate()",
            "A.publicAndPublic()",
            "B.publicAndPublic()");
  }

  static class TestClass {

    public static void main(String[] args) {
      A a = new A();
      B b = new B();
      a.privateAndPrivate();
      b.privateAndPrivate();
      a.privateAndPackagePrivate();
      b.privateAndPackagePrivate();
      a.privateAndPublic();
      b.privateAndPublic();
      a.packagePrivateAndPrivate();
      b.packagePrivateAndPrivate();
      a.packagePrivateAndPackagePrivate();
      b.packagePrivateAndPackagePrivate();
      a.packagePrivateAndPublic();
      b.packagePrivateAndPublic();
      a.publicAndPrivate();
      b.publicAndPrivate();
      a.publicAndPackagePrivate();
      b.publicAndPackagePrivate();
      a.publicAndPublic();
      b.publicAndPublic();
    }
  }

  @NeverClassInline
  static class A {

    @NeverInline
    private void privateAndPrivate() {
      System.out.println("A.privateAndPrivate()");
    }

    @NeverInline
    private void privateAndPackagePrivate() {
      System.out.println("A.privateAndPackagePrivate()");
    }

    @NeverInline
    private void privateAndPublic() {
      System.out.println("A.privateAndPublic()");
    }

    @NeverInline
    void packagePrivateAndPrivate() {
      System.out.println("A.packagePrivateAndPrivate()");
    }

    @NeverInline
    void packagePrivateAndPackagePrivate() {
      System.out.println("A.packagePrivateAndPackagePrivate()");
    }

    @NeverInline
    void packagePrivateAndPublic() {
      System.out.println("A.packagePrivateAndPublic()");
    }

    @NeverInline
    public void publicAndPrivate() {
      System.out.println("A.publicAndPrivate()");
    }

    @NeverInline
    public void publicAndPackagePrivate() {
      System.out.println("A.publicAndPackagePrivate()");
    }

    @NeverInline
    public void publicAndPublic() {
      System.out.println("A.publicAndPublic()");
    }
  }

  @NeverClassInline
  static class B {

    @NeverInline
    private void privateAndPrivate() {
      System.out.println("B.privateAndPrivate()");
    }

    @NeverInline
    void privateAndPackagePrivate() {
      System.out.println("B.privateAndPackagePrivate()");
    }

    @NeverInline
    public void privateAndPublic() {
      System.out.println("B.privateAndPublic()");
    }

    @NeverInline
    private void packagePrivateAndPrivate() {
      System.out.println("B.packagePrivateAndPrivate()");
    }

    @NeverInline
    void packagePrivateAndPackagePrivate() {
      System.out.println("B.packagePrivateAndPackagePrivate()");
    }

    @NeverInline
    public void packagePrivateAndPublic() {
      System.out.println("B.packagePrivateAndPublic()");
    }

    @NeverInline
    private void publicAndPrivate() {
      System.out.println("B.publicAndPrivate()");
    }

    @NeverInline
    void publicAndPackagePrivate() {
      System.out.println("B.publicAndPackagePrivate()");
    }

    @NeverInline
    public void publicAndPublic() {
      System.out.println("B.publicAndPublic()");
    }
  }
}
