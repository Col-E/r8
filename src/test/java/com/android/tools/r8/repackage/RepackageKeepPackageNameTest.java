// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RepackageKeepPackageNameTest extends RepackageTestBase {

  public RepackageKeepPackageNameTest(
      String flattenPackageHierarchyOrRepackageClasses, TestParameters parameters) {
    super(flattenPackageHierarchyOrRepackageClasses, parameters);
  }

  @Test
  public void testR8() throws Exception {
    R8TestCompileResult compileLib =
        testForR8(parameters.getBackend())
            .addInnerClasses(getClass())
            .setMinApi(parameters)
            .addKeepClassAndMembersRulesWithAllowObfuscation(
                ShouldStayInPackage.class, ShouldBeRepackaged.class)
            .addKeepPackageNamesRule(typeName(ShouldStayInPackage.class))
            .apply(this::configureRepackaging)
            .compile()
            .inspect(
                inspector -> {
                  assertThat(ShouldStayInPackage.class, isNotRepackaged(inspector));
                  assertThat(ShouldBeRepackaged.class, isRepackaged(inspector));
                });

    testForR8(parameters.getBackend())
        .addProgramClasses(Runner.class)
        .addClasspathClasses(ShouldStayInPackage.class, ShouldBeRepackaged.class)
        .addApplyMapping(compileLib.getProguardMap())
        .addKeepMainRule(Runner.class)
        .setMinApi(parameters)
        .compile()
        .addRunClasspathFiles(compileLib.writeToZip())
        .run(parameters.getRuntime(), Runner.class)
        .assertSuccessWithOutputLines("ShouldStayInPackage::foo", "ShouldBeRepackaged::bar");
  }

  public static class ShouldStayInPackage {

    static void foo() {
      System.out.println("ShouldStayInPackage::foo");
    }
  }

  public static class ShouldBeRepackaged {

    public static void bar() {
      System.out.println("ShouldBeRepackaged::bar");
    }
  }

  public static class Runner {

    public static void main(String[] args) {
      ShouldStayInPackage.foo();
      ShouldBeRepackaged.bar();
    }
  }
}
