// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.HorizontallyMergedClassesInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RepackageCloneTest extends RepackageTestBase {

  public RepackageCloneTest(
      String flattenPackageHierarchyOrRepackageClasses, TestParameters parameters) {
    super(flattenPackageHierarchyOrRepackageClasses, parameters);
  }

  @Test
  public void testClone() throws Exception {
    testForR8Compat(parameters.getBackend())
        .addProgramClasses(A.class, B.class, C.class, Main.class)
        .addKeepMainRule(Main.class)
        .addKeepClassRulesWithAllowObfuscation(A.class)
        // Ensure we keep values() which has a call to clone.
        .addKeepRules("-keepclassmembers class " + typeName(A.class) + " { *; }")
        .apply(this::configureRepackaging)
        .setMinApi(parameters)
        .addHorizontallyMergedClassesInspector(
            HorizontallyMergedClassesInspector::assertNoClassesMerged)
        .enableInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .compile()
        .inspect(
            inspector -> {
              assertThat(A.class, isRepackaged(inspector));
              assertThat(B.class, isRepackaged(inspector));
              assertThat(C.class, isRepackaged(inspector));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("foo", "null");
  }

  public enum A {
    foo;
  }

  @NoHorizontalClassMerging
  public static class B {

    @NeverInline
    public static void foo() {
      try {
        new B().clone();
      } catch (CloneNotSupportedException e) {
        e.printStackTrace();
      }
    }
  }

  @NoHorizontalClassMerging
  public static class C {

    @NeverInline
    public static void foo() {
      System.out.println(new A[10].clone()[1]);
      ;
    }
  }

  public static class Main {

    public static void main(String[] args) {
      System.out.println(A.foo);
      B.foo();
      C.foo();
    }
  }
}
