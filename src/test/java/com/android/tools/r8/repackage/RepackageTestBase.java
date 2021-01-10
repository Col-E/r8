// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import static com.android.tools.r8.shaking.ProguardConfigurationParser.FLATTEN_PACKAGE_HIERARCHY;
import static com.android.tools.r8.shaking.ProguardConfigurationParser.REPACKAGE_CLASSES;

import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.runners.Parameterized.Parameters;

public abstract class RepackageTestBase extends TestBase {

  private final String flattenPackageHierarchyOrRepackageClasses;
  protected final TestParameters parameters;

  @Parameters(name = "{1}, kind: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        ImmutableList.of(FLATTEN_PACKAGE_HIERARCHY, REPACKAGE_CLASSES),
        getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public RepackageTestBase(
      String flattenPackageHierarchyOrRepackageClasses,
      TestParameters parameters) {
    this.flattenPackageHierarchyOrRepackageClasses = flattenPackageHierarchyOrRepackageClasses;
    this.parameters = parameters;
  }

  protected String getRepackagePackage() {
    return "foo";
  }

  protected Matcher<Class<?>> isRepackaged(CodeInspector inspector) {
    return isRepackagedAsExpected(inspector, null, true);
  }

  protected Matcher<Class<?>> isRepackagedIf(
      CodeInspector inspector, boolean eligibleForRepackaging) {
    return isRepackagedAsExpected(inspector, null, eligibleForRepackaging);
  }

  protected Matcher<Class<?>> isNotRepackaged(CodeInspector inspector) {
    return isRepackagedAsExpected(inspector, null, false);
  }

  /**
   * Checks that the class of interest is repackaged as expected.
   *
   * <p>If building with -repackageclasses, it is checked that the given class of interest is
   * repackaged into "foo" (unless getRepackagePackage() is overridden). In this case, {@param
   * packageName} is unused.
   *
   * <p>If building with -flattenpackagehierarchy, it is checked that the given class is repackaged
   * into "foo.<packageName>".
   */
  protected Matcher<Class<?>> isRepackagedAsExpected(CodeInspector inspector, String packageName) {
    return isRepackagedAsExpected(inspector, packageName, true);
  }

  private Matcher<Class<?>> isRepackagedAsExpected(
      CodeInspector inspector, String packageName, boolean eligibleForRepackaging) {
    return new TypeSafeMatcher<Class<?>>() {
      @Override
      public boolean matchesSafely(Class<?> clazz) {
        ClassSubject classSubject = inspector.clazz(clazz);
        if (!classSubject.isPresent()) {
          return false;
        }
        return getActualPackage(classSubject).equals(getExpectedPackage(clazz));
      }

      @Override
      public void describeTo(Description description) {
        if (eligibleForRepackaging) {
          description.appendText(
              "class to be repackaged to '" + getExpectedPackageForEligibleClass() + "'");
        } else {
          description.appendText("class to be ineligible for repackaging");
        }
      }

      @Override
      public void describeMismatchSafely(Class<?> clazz, Description description) {
        ClassSubject classSubject = inspector.clazz(clazz);
        if (classSubject.isPresent()) {
          description
              .appendText("class ")
              .appendValue(clazz.getTypeName())
              .appendText(" was not (actual: '" + getActualPackage(classSubject) + "')");
        } else {
          description
              .appendText("class ")
              .appendValue(clazz.getTypeName())
              .appendText(" was absent");
        }
      }

      private String getActualPackage(ClassSubject classSubject) {
        return classSubject.getDexProgramClass().getType().getPackageName();
      }

      private String getExpectedPackage(Class<?> clazz) {
        return eligibleForRepackaging
            ? getExpectedPackageForEligibleClass()
            : clazz.getPackage().getName();
      }

      private String getExpectedPackageForEligibleClass() {
        List<String> expectedPackageNames = new ArrayList<>();
        expectedPackageNames.add(getRepackagePackage());
        if (isFlattenPackageHierarchy()) {
          expectedPackageNames.add(packageName != null ? packageName : "a");
        }
        return StringUtils.join(expectedPackageNames, ".");
      }
    };
  }

  protected void configureRepackaging(R8TestBuilder<?> testBuilder) {
    testBuilder.addKeepRules(
        "-" + flattenPackageHierarchyOrRepackageClasses + " \"" + getRepackagePackage() + "\"");
  }

  protected boolean isFlattenPackageHierarchy() {
    return flattenPackageHierarchyOrRepackageClasses.equals(FLATTEN_PACKAGE_HIERARCHY);
  }
}
