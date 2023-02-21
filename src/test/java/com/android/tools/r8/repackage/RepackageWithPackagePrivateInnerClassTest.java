// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.repackage.RepackageWithPackagePrivateInnerClassTest.IneligibleForRepackaging.NonPublicKeptClass;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RepackageWithPackagePrivateInnerClassTest extends RepackageTestBase {

  public RepackageWithPackagePrivateInnerClassTest(
      String flattenPackageHierarchyOrRepackageClasses, TestParameters parameters) {
    super(flattenPackageHierarchyOrRepackageClasses, parameters);
  }

  @Test
  public void testCompat() throws Exception {
    test(
        testForR8Compat(parameters.getBackend()).addKeepClassRules(NonPublicKeptClass.class),
        false);
  }

  @Test
  public void testFull() throws Exception {
    test(testForR8(parameters.getBackend()).addKeepClassRules(NonPublicKeptClass.class), true);
  }

  private void test(R8TestBuilder<?> builder, boolean expectRepackaged) throws Exception {
    builder
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .addKeepAttributeInnerClassesAndEnclosingMethod()
        .apply(this::configureRepackaging)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(inspector -> inspect(inspector, expectRepackaged))
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private void inspect(CodeInspector inspector, boolean expectRepackaged) {
    ClassSubject classSubject = inspector.clazz(IneligibleForRepackaging.class);
    assertThat(classSubject, isPresent());

    // Verify that the class was not repackaged.
    if (expectRepackaged) {
      assertNotEquals(
          IneligibleForRepackaging.class.getPackage().getName(),
          classSubject.getDexProgramClass().getType().getPackageName());
    } else {
      assertEquals(
          IneligibleForRepackaging.class.getPackage().getName(),
          classSubject.getDexProgramClass().getType().getPackageName());
    }
  }

  public static class TestClass {

    public static void main(String[] args) {
      IneligibleForRepackaging.greet();
    }
  }

  public static class IneligibleForRepackaging {

    @NeverInline
    public static void greet() {
      System.out.println("Hello world!");
    }

    static class NonPublicKeptClass {}
  }
}
