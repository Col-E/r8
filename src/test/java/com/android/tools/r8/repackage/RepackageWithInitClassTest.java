// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import static com.android.tools.r8.shaking.ProguardConfigurationParser.FLATTEN_PACKAGE_HIERARCHY;
import static com.android.tools.r8.shaking.ProguardConfigurationParser.REPACKAGE_CLASSES;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverPropagateValue;
import com.android.tools.r8.NoAccessModification;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RepackageWithInitClassTest extends RepackageTestBase {

  private final boolean enableMemberValuePropagationAnnotations;

  @Parameters(name = "{2}, kind: {1}, @NeverPropagateValue: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(),
        ImmutableList.of(FLATTEN_PACKAGE_HIERARCHY, REPACKAGE_CLASSES),
        getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public RepackageWithInitClassTest(
      boolean enableMemberValuePropagationAnnotations,
      String flattenPackageHierarchyOrRepackageClasses,
      TestParameters parameters) {
    super(flattenPackageHierarchyOrRepackageClasses, parameters);
    this.enableMemberValuePropagationAnnotations = enableMemberValuePropagationAnnotations;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addClassObfuscationDictionary("a")
        .addKeepMainRule(TestClass.class)
        .addMemberValuePropagationAnnotations()
        .apply(this::configureRepackaging)
        .enableMemberValuePropagationAnnotations(enableMemberValuePropagationAnnotations)
        .enableNoAccessModificationAnnotationsForMembers()
        .addOptionsModification(options -> options.enableRedundantFieldLoadElimination = false)
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject repackagedClassSubject = inspector.clazz(StaticMemberValuePropagation.class);
    assertThat(repackagedClassSubject, isPresent());

    String clinitFieldName = inspector.getFactory().objectMembers.clinitField.name.toSourceString();
    if (enableMemberValuePropagationAnnotations) {
      // No $r8$clinit field should have been synthesized since we can use the HELLO field.
      assertThat(
          repackagedClassSubject.uniqueFieldWithOriginalName(clinitFieldName), not(isPresent()));
      assertThat(repackagedClassSubject.uniqueFieldWithOriginalName("HELLO"), isPresent());

      // Verify that the WORLD field has been removed.
      assertThat(repackagedClassSubject.uniqueFieldWithOriginalName("WORLD"), not(isPresent()));

      // Verify that the class was not repackaged.
      assertThat(StaticMemberValuePropagation.class, isNotRepackaged(inspector));
    } else {
      // Verify that a $r8$clinit field was synthesized.
      assertThat(repackagedClassSubject.uniqueFieldWithOriginalName(clinitFieldName), isPresent());

      // Verify that both fields have been removed.
      assertThat(repackagedClassSubject.uniqueFieldWithOriginalName("HELLO"), not(isPresent()));
      assertThat(repackagedClassSubject.uniqueFieldWithOriginalName("WORLD"), not(isPresent()));

      // Verify that the class was repackaged.
      assertThat(StaticMemberValuePropagation.class, isRepackaged(inspector));
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(StaticMemberValuePropagation.WORLD);
    }
  }

  public static class StaticMemberValuePropagation {

    @NeverPropagateValue @NoAccessModification static String HELLO = "Hello";

    public static String WORLD = " world!";

    static {
      System.out.print(HELLO);
    }
  }
}
