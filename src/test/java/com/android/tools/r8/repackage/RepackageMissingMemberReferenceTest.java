// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestParameters;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RepackageMissingMemberReferenceTest extends RepackageTestBase {

  private final String EXPECTED = "MissingReference::doSomething";

  public RepackageMissingMemberReferenceTest(
      String flattenPackageHierarchyOrRepackageClasses, TestParameters parameters) {
    super(flattenPackageHierarchyOrRepackageClasses, parameters);
  }

  @Test
  public void testR8WithoutRepackaging() throws Exception {
    runTest(false).assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    runTest(true).assertSuccessWithOutputLines(EXPECTED);
  }

  private R8TestRunResult runTest(boolean repackage) throws Exception {
    return testForR8(parameters.getBackend())
        .addProgramClasses(ClassWithMissingReferenceInCode.class, Main.class)
        .addKeepMainRule(Main.class)
        .applyIf(repackage, this::configureRepackaging)
        .setMinApi(parameters.getApiLevel())
        .addDontWarn(MissingReference.class)
        .enableInliningAnnotations()
        .compile()
        .inspect(
            inspector ->
                assertThat(ClassWithMissingReferenceInCode.class, isNotRepackaged(inspector)))
        .addRunClasspathClasses(MissingReference.class)
        .run(parameters.getRuntime(), Main.class);
  }

  static class MissingReference {
    public static void doSomething() {
      System.out.println("MissingReference::doSomething");
    }
  }

  public static class ClassWithMissingReferenceInCode {

    @NeverInline
    public static void test() {
      MissingReference.doSomething();
    }
  }

  public static class Main {
    public static void main(String[] args) {
      ClassWithMissingReferenceInCode.test();
    }
  }
}
