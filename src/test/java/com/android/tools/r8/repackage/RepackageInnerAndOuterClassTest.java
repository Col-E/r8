// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.repackage.RepackageInnerAndOuterClassTest.Outer.Inner;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RepackageInnerAndOuterClassTest extends RepackageTestBase {

  public RepackageInnerAndOuterClassTest(
      String flattenPackageHierarchyOrRepackageClasses, TestParameters parameters) {
    super(flattenPackageHierarchyOrRepackageClasses, parameters);
  }

  @Test
  public void testR8() throws Exception {
    test(
        testForR8(parameters.getBackend())
            // TODO(b/169750465): It should not be necessary to explicitly remove the inner class
            //  attributes from TestClass to allow repackaging of Inner and Outer.
            .addProgramClassFileData(transformer(TestClass.class).removeInnerClasses().transform()),
        true);
  }

  @Test
  public void testR8Compat() throws Exception {
    test(testForR8Compat(parameters.getBackend()).addProgramClasses(TestClass.class), false);
  }

  private void test(R8TestBuilder<?> testBuilder, boolean eligibleForRepackaging) throws Exception {
    testBuilder
        .addProgramClasses(Outer.class, Inner.class)
        .addKeepMainRule(TestClass.class)
        .addKeepAttributeInnerClassesAndEnclosingMethod()
        .apply(this::configureRepackaging)
        .enableInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(inspector -> inspect(inspector, eligibleForRepackaging))
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private void inspect(CodeInspector inspector, boolean eligibleForRepackaging) {
    assertThat(Outer.class, isRepackagedIf(inspector, eligibleForRepackaging));
    assertThat(Inner.class, isRepackagedIf(inspector, eligibleForRepackaging));
  }

  public static class TestClass {

    public static void main(String[] args) {
      Outer.greet();
      Inner.greet();
    }
  }

  @NoHorizontalClassMerging
  public static class Outer {

    @NeverInline
    public static void greet() {
      System.out.print("Hello");
    }

    @NoHorizontalClassMerging
    public static class Inner {

      @NeverInline
      public static void greet() {
        System.out.println(" world!");
      }
    }
  }
}
