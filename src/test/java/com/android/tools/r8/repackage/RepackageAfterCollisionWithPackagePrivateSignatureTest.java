// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RepackageAfterCollisionWithPackagePrivateSignatureTest extends RepackageTestBase {

  public RepackageAfterCollisionWithPackagePrivateSignatureTest(
      String flattenPackageHierarchyOrRepackageClasses, TestParameters parameters) {
    super(flattenPackageHierarchyOrRepackageClasses, parameters);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(RepackageAfterCollisionWithPackagePrivateSignatureTest.class)
        .addKeepClassAndMembersRules(TestClass.class)
        .addClassObfuscationDictionary("a")
        .apply(this::configureRepackaging)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private void inspect(CodeInspector inspector) {
    assertThat(RepackageCandidate.class, isRepackaged(inspector));
  }

  public static class TestClass {

    public static void main(String[] args) {
      RepackageCandidate.foo(0);
      RepackageCandidate.foo(System.currentTimeMillis(), 0);
    }

    static void restrictToCurrentPackage() {
      System.out.print("Hello");
    }
  }

  public static class RepackageCandidate {

    public static void foo(long unused) {
      TestClass.restrictToCurrentPackage();
    }

    @NeverInline
    public static void foo(long used, int unused) {
      if (used >= 0) {
        System.out.println(" world!");
      }
    }
  }
}
