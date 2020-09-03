// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.repackage.testclasses.repackagetest.TestClass;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RepackageAfterCollisionWithPackagePrivateSignatureTest extends TestBase {

  private static final String FLATTEN_PACKAGE_HIERARCHY = "flattenpackagehierarchy";
  private static final String REPACKAGE_CLASSES = "repackageclasses";
  private static final String REPACKAGE_DIR = "foo";

  private final String flattenPackageHierarchyOrRepackageClasses;
  private final TestParameters parameters;

  @Parameters(name = "{1}, kind: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        ImmutableList.of(FLATTEN_PACKAGE_HIERARCHY, REPACKAGE_CLASSES),
        getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public RepackageAfterCollisionWithPackagePrivateSignatureTest(
      String flattenPackageHierarchyOrRepackageClasses, TestParameters parameters) {
    this.flattenPackageHierarchyOrRepackageClasses = flattenPackageHierarchyOrRepackageClasses;
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    testForR8(parameters.getBackend())
        .addInnerClasses(RepackageAfterCollisionWithPackagePrivateSignatureTest.class)
        .addKeepClassAndMembersRules(TestClass.class)
        .addKeepRules(
            "-" + flattenPackageHierarchyOrRepackageClasses + " \"" + REPACKAGE_DIR + "\"")
        .addClassObfuscationDictionary("a")
        .addOptionsModification(options -> options.testing.enableExperimentalRepackaging = true)
        .enableInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject repackageClassSubject = inspector.clazz(RepackageCandidate.class);
    assertThat(repackageClassSubject, isPresent());
    assertEquals(
        flattenPackageHierarchyOrRepackageClasses.equals(FLATTEN_PACKAGE_HIERARCHY)
            ? REPACKAGE_DIR + ".a"
            : REPACKAGE_DIR,
        repackageClassSubject.getDexProgramClass().getType().getPackageName());
  }

  public static class TestClass {

    public static void main(String[] args) {
      RepackageCandidate.foo(0);
      RepackageCandidate.foo((int) System.currentTimeMillis(), 0);
    }

    static void restrictToCurrentPackage() {
      System.out.print("Hello");
    }
  }

  public static class RepackageCandidate {

    public static void foo(int unused) {
      TestClass.restrictToCurrentPackage();
    }

    @NeverInline
    public static void foo(int used, int unused) {
      if (used >= 0) {
        System.out.println(" world!");
      }
    }
  }
}
