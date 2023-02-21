// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import static com.android.tools.r8.shaking.ProguardConfigurationParser.FLATTEN_PACKAGE_HIERARCHY;
import static com.android.tools.r8.shaking.ProguardConfigurationParser.REPACKAGE_CLASSES;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RepackageWithKeepPackagePrivateTest extends RepackageTestBase {

  private final boolean allowAccessModification;

  @Parameters(name = "{2}, kind: {1}, access modification: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(),
        ImmutableList.of(FLATTEN_PACKAGE_HIERARCHY, REPACKAGE_CLASSES),
        getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public RepackageWithKeepPackagePrivateTest(
      boolean allowAccessModification,
      String flattenPackageHierarchyOrRepackageClasses,
      TestParameters parameters) {
    super(flattenPackageHierarchyOrRepackageClasses, parameters);
    this.allowAccessModification = allowAccessModification;
  }

  @Test
  public void test() throws Exception {
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addInnerClasses(getClass())
            .addKeepRules(getKeepRules())
            .addKeepPackageNamesRule(getClass().getPackage())
            .allowAccessModification(allowAccessModification)
            .apply(this::configureRepackaging)
            .setMinApi(parameters)
            .compile()
            .inspect(this::inspect);

    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .addClasspathClasses(A.class, B.class)
        .addKeepAllClassesRule()
        .addApplyMapping(compileResult.getProguardMap())
        .setMinApi(parameters)
        .compile()
        .addRunClasspathFiles(compileResult.writeToZip())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private List<String> getKeepRules() {
    String modifiers = "allowobfuscation";
    if (allowAccessModification) {
      modifiers += ",allowaccessmodification";
    }
    return ImmutableList.of(
        "-keep," + modifiers + " class " + A.class.getTypeName() + " { void greet(); }",
        "-keep," + modifiers + " class " + B.class.getTypeName() + " { void greet(); }");
  }

  private void inspect(CodeInspector inspector) {
    assertThat(A.class, isNotRepackaged(inspector));
    assertThat(B.class, isNotRepackaged(inspector));
  }

  public static class TestClass {

    public static void main(String[] args) {
      A.greet();
      B.greet();
    }
  }

  static class A {

    public static void greet() {
      System.out.print("Hello");
    }
  }

  public static class B {

    static void greet() {
      System.out.println(" world!");
    }
  }
}
