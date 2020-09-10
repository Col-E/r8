// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import static com.android.tools.r8.shaking.ProguardConfigurationParser.FLATTEN_PACKAGE_HIERARCHY;
import static com.android.tools.r8.shaking.ProguardConfigurationParser.REPACKAGE_CLASSES;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RepackageWithInitClassTest extends TestBase {

  private static final String REPACKAGE_PACKAGE = "foo";

  private final String flattenPackageHierarchyOrRepackageClasses;
  private final TestParameters parameters;

  @Parameters(name = "{1}, kind: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        ImmutableList.of(FLATTEN_PACKAGE_HIERARCHY, REPACKAGE_CLASSES),
        getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public RepackageWithInitClassTest(
      String flattenPackageHierarchyOrRepackageClasses, TestParameters parameters) {
    this.flattenPackageHierarchyOrRepackageClasses = flattenPackageHierarchyOrRepackageClasses;
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addClassObfuscationDictionary("a")
        .addKeepMainRule(TestClass.class)
        .addKeepRules(
            "-" + flattenPackageHierarchyOrRepackageClasses + " \"" + REPACKAGE_PACKAGE + "\"")
        .addOptionsModification(
            options -> {
              assert !options.testing.enableExperimentalRepackaging;
              options.testing.enableExperimentalRepackaging = true;
            })
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject repackagedClassSubject = inspector.clazz(StaticMemberValuePropagation.class);
    assertThat(repackagedClassSubject, isPresent());

    // Verify that a $r8$clinit field was synthesized.
    String clinitFieldName = inspector.getFactory().objectMembers.clinitField.name.toSourceString();
    assertThat(repackagedClassSubject.uniqueFieldWithName(clinitFieldName), isPresent());
    assertThat(repackagedClassSubject.uniqueFieldWithName("GREETING"), not(isPresent()));

    // Verify that the class was repackaged.
    assertEquals(
        flattenPackageHierarchyOrRepackageClasses.equals(FLATTEN_PACKAGE_HIERARCHY)
            ? REPACKAGE_PACKAGE + ".a"
            : REPACKAGE_PACKAGE,
        repackagedClassSubject.getDexProgramClass().getType().getPackageName());
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(StaticMemberValuePropagation.GREETING);
    }
  }

  public static class StaticMemberValuePropagation {

    public static String GREETING = " world!";

    static {
      System.out.print("Hello");
    }
  }
}
