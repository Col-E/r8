// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import static com.android.tools.r8.shaking.ProguardConfigurationParser.FLATTEN_PACKAGE_HIERARCHY;
import static com.android.tools.r8.shaking.ProguardConfigurationParser.REPACKAGE_CLASSES;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject.JumboStringMode;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RepackageWithDexItemBasedConstStringTest extends RepackageTestBase {

  @Parameters(name = "{1}, kind: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        ImmutableList.of(FLATTEN_PACKAGE_HIERARCHY, REPACKAGE_CLASSES),
        getTestParameters()
            .withAllRuntimes()
            .withApiLevelsEndingAtExcluding(AndroidApiLevel.L)
            .build());
  }

  public RepackageWithDexItemBasedConstStringTest(
      String flattenPackageHierarchyOrRepackageClasses, TestParameters parameters) {
    super(flattenPackageHierarchyOrRepackageClasses, parameters);
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .addMainDexClassRules(TestClass.class)
        .apply(this::configureRepackaging)
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject testClassSubject = inspector.clazz(TestClass.class);
    assertThat(testClassSubject, isPresent());

    ClassSubject aClassSubject = inspector.clazz(A.class);
    assertThat(aClassSubject, isPresent());

    MethodSubject mainMethodSubject = testClassSubject.mainMethod();
    assertThat(mainMethodSubject, isPresent());
    assertTrue(
        mainMethodSubject
            .streamInstructions()
            .anyMatch(x -> x.isConstString(aClassSubject.getFinalName(), JumboStringMode.ALLOW)));
  }

  public static class TestClass {

    public static void main(String[] args) throws Exception {
      Class.forName("com.android.tools.r8.repackage.RepackageWithDexItemBasedConstStringTest$A")
          .getConstructor()
          .newInstance();
    }
  }

  public static class A {

    static {
      System.out.print("Hello");
    }

    public A() {
      System.out.println(" world!");
    }
  }
}
