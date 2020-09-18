// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import static com.android.tools.r8.shaking.ProguardConfigurationParser.FLATTEN_PACKAGE_HIERARCHY;
import static com.android.tools.r8.shaking.ProguardConfigurationParser.REPACKAGE_CLASSES;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.R8FullTestBuilder;
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

  protected static final String REPACKAGE_PACKAGE = "foo";

  private final boolean enableExperimentalRepackaging;
  private final String flattenPackageHierarchyOrRepackageClasses;
  protected final TestParameters parameters;

  @Parameters(name = "{1}, kind: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        ImmutableList.of(FLATTEN_PACKAGE_HIERARCHY, REPACKAGE_CLASSES),
        getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public RepackageTestBase(
      String flattenPackageHierarchyOrRepackageClasses, TestParameters parameters) {
    this(true, flattenPackageHierarchyOrRepackageClasses, parameters);
  }

  public RepackageTestBase(
      boolean enableExperimentalRepackaging,
      String flattenPackageHierarchyOrRepackageClasses,
      TestParameters parameters) {
    this.enableExperimentalRepackaging = enableExperimentalRepackaging;
    this.flattenPackageHierarchyOrRepackageClasses = flattenPackageHierarchyOrRepackageClasses;
    this.parameters = parameters;
  }

  protected Matcher<Class<?>> isRepackaged(CodeInspector inspector) {
    return isRepackagedIf(inspector, true);
  }

  protected Matcher<Class<?>> isRepackagedIf(
      CodeInspector inspector, boolean eligibleForRepackaging) {
    return new TypeSafeMatcher<Class<?>>() {
      @Override
      public boolean matchesSafely(Class<?> clazz) {
        ClassSubject classSubject = inspector.clazz(clazz);
        if (!classSubject.isPresent()) {
          return false;
        }

        String expectedPackage;
        if (eligibleForRepackaging) {
          List<String> expectedPackageNames = new ArrayList<>();
          expectedPackageNames.add(REPACKAGE_PACKAGE);
          if (isFlattenPackageHierarchy()) {
            expectedPackageNames.add("a");
          }

          expectedPackage = StringUtils.join(expectedPackageNames, ".");
        } else {
          expectedPackage = clazz.getPackage().getName();
        }

        return classSubject.getDexProgramClass().getType().getPackageName().equals(expectedPackage);
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("class to be repackaged");
      }

      @Override
      public void describeMismatchSafely(Class<?> clazz, Description description) {
        description.appendText("class ").appendValue(clazz.getTypeName()).appendText(" was not");
      }
    };
  }

  protected void configureRepackaging(R8FullTestBuilder testBuilder) {
    testBuilder
        .addKeepRules(
            "-" + flattenPackageHierarchyOrRepackageClasses + " \"" + REPACKAGE_PACKAGE + "\"")
        .addOptionsModification(
            options -> {
              assertFalse(options.testing.enableExperimentalRepackaging);
              options.testing.enableExperimentalRepackaging = enableExperimentalRepackaging;
            });
  }

  protected boolean isExperimentalRepackaging() {
    return enableExperimentalRepackaging;
  }

  protected boolean isFlattenPackageHierarchy() {
    return flattenPackageHierarchyOrRepackageClasses.equals(FLATTEN_PACKAGE_HIERARCHY);
  }
}
