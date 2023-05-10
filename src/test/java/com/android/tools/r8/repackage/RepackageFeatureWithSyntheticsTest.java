// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RepackageFeatureWithSyntheticsTest extends RepackageTestBase {

  public RepackageFeatureWithSyntheticsTest(
      String flattenPackageHierarchyOrRepackageClasses, TestParameters parameters) {
    super(flattenPackageHierarchyOrRepackageClasses, parameters);
  }

  private static final Class<?> FIRST_FOO =
      com.android.tools.r8.repackage.testclasses.repackagefeaturewithsynthetics.first.Foo.class;

  private static final Class<?> FIRST_PKG_PRIVATE =
      com.android.tools.r8.repackage.testclasses.repackagefeaturewithsynthetics.first
          .PkgProtectedMethod.class;

  private static final List<Class<?>> FIRST_CLASSES =
      ImmutableList.of(FIRST_FOO, FIRST_PKG_PRIVATE);

  private static final Class<?> FIRST_FIRST_FOO =
      com.android.tools.r8.repackage.testclasses.repackagefeaturewithsynthetics.first.first.Foo
          .class;

  private static final Class<?> FIRST_FIRST_PKG_PRIVATE =
      com.android.tools.r8.repackage.testclasses.repackagefeaturewithsynthetics.first.first
          .PkgProtectedMethod.class;

  private static final List<Class<?>> FIRST_FIRST_CLASSES =
      ImmutableList.of(FIRST_FIRST_FOO, FIRST_FIRST_PKG_PRIVATE);

  private static List<Class<?>> getTestClasses() {
    return ImmutableList.<Class<?>>builder()
        .addAll(getBaseClasses())
        .add(TestClass.class)
        .add(I.class)
        .build();
  }

  private static List<Class<?>> getBaseClasses() {
    return FIRST_CLASSES;
  }

  private static List<Class<?>> getFeatureClasses() {
    return FIRST_FIRST_CLASSES;
  }

  private static String EXPECTED = StringUtils.lines("first.Foo", "first.first.Foo");

  @Override
  public String getRepackagePackage() {
    return "dest";
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(getTestClasses())
        .addProgramClasses(getFeatureClasses())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void test() throws Exception {
    assumeTrue("Feature splits require DEX output.", parameters.isDexRuntime());
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(getTestClasses())
            .addFeatureSplit(getFeatureClasses().toArray(new Class<?>[0]))
            .addKeepMainRule(TestClass.class)
            .addKeepClassAndMembersRules(FIRST_PKG_PRIVATE, FIRST_FIRST_PKG_PRIVATE)
            .addKeepClassAndMembersRules(I.class)
            .addKeepMethodRules(
                Reference.methodFromMethod(TestClass.class.getDeclaredMethod("run", I.class)))
            .addKeepAttributeInnerClassesAndEnclosingMethod()
            .apply(this::configureRepackaging)
            .enableInliningAnnotations()
            .enableNeverClassInliningAnnotations()
            .setMinApi(parameters)
            .compile();

    // Each Foo class will give rise to a single lambda.
    int expectedSyntheticsInBase = 1;
    int expectedSyntheticsInFeature = 1;

    // Check that the first.Foo is repackaged but that the pkg private access class is not.
    // The implies that the lambda which is doing a package private access cannot be repackaged.
    // If it is, the access will fail resulting in a runtime exception.
    compileResult.inspect(
        baseInspector -> {
          assertThat(FIRST_FOO, isRepackagedAsExpected(baseInspector, "a"));
          assertThat(FIRST_PKG_PRIVATE, isNotRepackaged(baseInspector));
          assertEquals(
              getTestClasses().size() + expectedSyntheticsInBase,
              baseInspector.allClasses().size());
        },
        featureInspector -> {
          assertThat(FIRST_FIRST_FOO, isRepackagedAsExpected(featureInspector, "b"));
          assertThat(FIRST_FIRST_PKG_PRIVATE, isNotRepackaged(featureInspector));
          assertEquals(
              getFeatureClasses().size() + expectedSyntheticsInFeature,
              featureInspector.allClasses().size());
        });

    compileResult
        .addFeatureSplitsToRunClasspathFiles()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("first.Foo", "first.first.Foo");
  }

  public interface I {
    void run();
  }

  public static class TestClass {

    // Public kept run method to accept a lambda ensuring desugaring which cannot be optimized out.
    public static void run(I i) {
      i.run();
    }

    public static void main(String[] args) {
      new com.android.tools.r8.repackage.testclasses.repackagefeaturewithsynthetics.first.Foo();
      new com.android.tools.r8.repackage.testclasses.repackagefeaturewithsynthetics.first.first
          .Foo();
    }
  }
}
