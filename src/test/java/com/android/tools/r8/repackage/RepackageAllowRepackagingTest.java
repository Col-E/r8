// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NoAccessModification;
import com.android.tools.r8.TestParameters;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RepackageAllowRepackagingTest extends RepackageTestBase {

  public RepackageAllowRepackagingTest(
      String flattenPackageHierarchyOrRepackageClasses, TestParameters parameters) {
    super(flattenPackageHierarchyOrRepackageClasses, parameters);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .apply(this::configureRepackaging)
        .addKeepRules(
            "-keep,allowrepackage class " + typeName(ShouldStayInPackage.class) + " { *; }")
        .addKeepRules(
            "-keep,allowrepackage class " + typeName(ShouldBeRepackaged.class) + " { *; }")
        .enableNoAccessModificationAnnotationsForMembers()
        .compile()
        .inspect(
            inspector -> {
              assertThat(ShouldStayInPackage.class, isNotRepackaged(inspector));
              assertThat(ShouldBeRepackaged.class, isRepackaged(inspector));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("ShouldStayInPackage::foo", "ShouldBeRepackaged::bar");
  }

  public static class ShouldStayInPackage {

    @NoAccessModification
    static void foo() {
      System.out.println("ShouldStayInPackage::foo");
    }
  }

  public static class ShouldBeRepackaged {

    public static void bar() {
      System.out.println("ShouldBeRepackaged::bar");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      ShouldStayInPackage.foo();
      ShouldBeRepackaged.bar();
    }
  }
}
