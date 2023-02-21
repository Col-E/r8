// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestParameters;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RepackageProtectedInSeparatePackageTest extends RepackageTestBase {

  private final String EXPECTED = "Base::foo";

  public RepackageProtectedInSeparatePackageTest(
      String flattenPackageHierarchyOrRepackageClasses, TestParameters parameters) {
    super(flattenPackageHierarchyOrRepackageClasses, parameters);
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(Base.class, Sub.class, Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8WithRepackage() throws Exception {
    R8TestRunResult runResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(Base.class, Sub.class, Main.class)
            .addKeepMainRule(Main.class)
            .setMinApi(parameters)
            .addKeepClassAndMembersRules(Base.class)
            .apply(this::configureRepackaging)
            .enableInliningAnnotations()
            .enableNeverClassInliningAnnotations()
            .compile()
            .inspect(
                inspector -> {
                  if (parameters.isCfRuntime()) {
                    assertThat(Sub.class, not(isRepackaged(inspector)));
                  } else {
                    assertThat(Sub.class, isRepackaged(inspector));
                  }
                })
            .run(parameters.getRuntime(), Main.class)
            .assertSuccessWithOutputLines(EXPECTED);
  }

  public static class Base {
    protected void foo() {
      System.out.println("Base::foo");
    }
  }

  @NeverClassInline
  public static class Sub extends Base {

    @NeverInline
    public void callFoo(Base base) {
      base.foo();
    }
  }

  public static class Main {

    public static void main(String[] args) {
      new Sub().callFoo(new Sub());
    }
  }
}
