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
import com.android.tools.r8.repackage.testclasses.RepackagingWithNonReboundFieldReferenceTestClasses;
import com.android.tools.r8.repackage.testclasses.RepackagingWithNonReboundFieldReferenceTestClasses.B;
import com.android.tools.r8.utils.BooleanUtils;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RepackagingWithNonReboundFieldReferenceTest extends TestBase {

  private static final String REPACKAGE_DIR = "foo";

  private final boolean alwaysUseExistingFieldAccessInfoCollectionInMemberRebinding;
  private final String flattenPackageHierarchyOrRepackageClasses;
  private final TestParameters parameters;

  @Parameters(name = "{2}, reuse FieldAccessInfoCollection: {0}, kind: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(),
        ImmutableList.of(FLATTEN_PACKAGE_HIERARCHY, REPACKAGE_CLASSES),
        getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public RepackagingWithNonReboundFieldReferenceTest(
      boolean alwaysUseExistingFieldAccessInfoCollectionInMemberRebinding,
      String flattenPackageHierarchyOrRepackageClasses,
      TestParameters parameters) {
    this.alwaysUseExistingFieldAccessInfoCollectionInMemberRebinding =
        alwaysUseExistingFieldAccessInfoCollectionInMemberRebinding;
    this.flattenPackageHierarchyOrRepackageClasses = flattenPackageHierarchyOrRepackageClasses;
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass(), RepackagingWithNonReboundFieldReferenceTestClasses.class)
        .addKeepMainRule(TestClass.class)
        .addKeepRules(
            "-" + flattenPackageHierarchyOrRepackageClasses + " \"" + REPACKAGE_DIR + "\"")
        .addOptionsModification(
            options -> {
              assertTrue(
                  options.testing.alwaysUseExistingFieldAccessInfoCollectionInMemberRebinding);
              options.testing.alwaysUseExistingFieldAccessInfoCollectionInMemberRebinding =
                  alwaysUseExistingFieldAccessInfoCollectionInMemberRebinding;
              assertFalse(options.testing.enableExperimentalRepackaging);
              options.testing.enableExperimentalRepackaging = true;
            })
        .enableMemberValuePropagationAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(B.GREETING);
    }
  }
}
