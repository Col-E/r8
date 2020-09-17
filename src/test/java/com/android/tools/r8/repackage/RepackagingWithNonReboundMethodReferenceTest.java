// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import static com.android.tools.r8.shaking.ProguardConfigurationParser.FLATTEN_PACKAGE_HIERARCHY;
import static com.android.tools.r8.shaking.ProguardConfigurationParser.REPACKAGE_CLASSES;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.repackage.testclasses.RepackagingWithNonReboundMethodReferenceTestClasses;
import com.android.tools.r8.repackage.testclasses.RepackagingWithNonReboundMethodReferenceTestClasses.B;
import com.android.tools.r8.utils.BooleanUtils;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RepackagingWithNonReboundMethodReferenceTest extends TestBase {

  private static final String REPACKAGE_DIR = "foo";

  private final boolean alwaysUseExistingAccessInfoCollectionsInMemberRebinding;
  private final String flattenPackageHierarchyOrRepackageClasses;
  private final TestParameters parameters;

  @Parameters(name = "{2}, use access info collections: {0}, kind: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(),
        ImmutableList.of(FLATTEN_PACKAGE_HIERARCHY, REPACKAGE_CLASSES),
        getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public RepackagingWithNonReboundMethodReferenceTest(
      boolean alwaysUseExistingAccessInfoCollectionsInMemberRebinding,
      String flattenPackageHierarchyOrRepackageClasses,
      TestParameters parameters) {
    this.alwaysUseExistingAccessInfoCollectionsInMemberRebinding =
        alwaysUseExistingAccessInfoCollectionsInMemberRebinding;
    this.flattenPackageHierarchyOrRepackageClasses = flattenPackageHierarchyOrRepackageClasses;
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass(), RepackagingWithNonReboundMethodReferenceTestClasses.class)
        .addKeepMainRule(TestClass.class)
        .addKeepRules(
            "-" + flattenPackageHierarchyOrRepackageClasses + " \"" + REPACKAGE_DIR + "\"")
        .addOptionsModification(
            options -> {
              assertTrue(options.testing.alwaysUseExistingAccessInfoCollectionsInMemberRebinding);
              options.testing.alwaysUseExistingAccessInfoCollectionsInMemberRebinding =
                  alwaysUseExistingAccessInfoCollectionsInMemberRebinding;
              assertFalse(options.testing.enableExperimentalRepackaging);
              options.testing.enableExperimentalRepackaging = true;
            })
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  static class TestClass {

    public static void main(String[] args) {
      new B().greet();
    }
  }
}
