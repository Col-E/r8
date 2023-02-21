// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import static com.android.tools.r8.shaking.ProguardConfigurationParser.FLATTEN_PACKAGE_HIERARCHY;
import static com.android.tools.r8.shaking.ProguardConfigurationParser.REPACKAGE_CLASSES;
import static org.junit.Assert.assertTrue;

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
public class RepackageWithNonReboundMethodReferenceTest extends RepackageTestBase {

  private final boolean alwaysUseExistingAccessInfoCollectionsInMemberRebinding;

  @Parameters(name = "{2}, use access info collections: {0}, kind: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(),
        ImmutableList.of(FLATTEN_PACKAGE_HIERARCHY, REPACKAGE_CLASSES),
        getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public RepackageWithNonReboundMethodReferenceTest(
      boolean alwaysUseExistingAccessInfoCollectionsInMemberRebinding,
      String flattenPackageHierarchyOrRepackageClasses,
      TestParameters parameters) {
    super(flattenPackageHierarchyOrRepackageClasses, parameters);
    this.alwaysUseExistingAccessInfoCollectionsInMemberRebinding =
        alwaysUseExistingAccessInfoCollectionsInMemberRebinding;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass(), RepackagingWithNonReboundMethodReferenceTestClasses.class)
        .addKeepMainRule(TestClass.class)
        .addOptionsModification(
            options -> {
              assertTrue(options.testing.alwaysUseExistingAccessInfoCollectionsInMemberRebinding);
              options.testing.alwaysUseExistingAccessInfoCollectionsInMemberRebinding =
                  alwaysUseExistingAccessInfoCollectionsInMemberRebinding;
            })
        .apply(this::configureRepackaging)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
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
