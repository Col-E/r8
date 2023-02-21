// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static com.android.tools.r8.shaking.ProguardConfigurationParser.FLATTEN_PACKAGE_HIERARCHY;
import static com.android.tools.r8.shaking.ProguardConfigurationParser.REPACKAGE_CLASSES;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.errors.UnsupportedMainDexListUsageDiagnostic;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

// TODO(b/181858113): This test is likely obsolete once main-dex-list support is removed.
@RunWith(Parameterized.class)
public class RepackageWithMainDexListTest extends RepackageTestBase {

  @Parameters(name = "{1}, kind: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        ImmutableList.of(FLATTEN_PACKAGE_HIERARCHY, REPACKAGE_CLASSES),
        getTestParameters()
            .withDexRuntimes()
            .withApiLevelsEndingAtExcluding(apiLevelWithNativeMultiDexSupport())
            .build());
  }

  public RepackageWithMainDexListTest(
      String flattenPackageHierarchyOrRepackageClasses, TestParameters parameters) {
    super(flattenPackageHierarchyOrRepackageClasses, parameters);
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        // -keep,allowobfuscation does not prohibit repackaging.
        .addKeepClassRulesWithAllowObfuscation(TestClass.class, OtherTestClass.class)
        .addKeepRules(
            "-keepclassmembers class " + TestClass.class.getTypeName() + " { <methods>; }")
        // Add a class that will be repackaged to the main dex list.
        .addMainDexListClasses(TestClass.class)
        .apply(this::configureRepackaging)
        // Debug mode to enable minimal main dex.
        .debug()
        .setMinApi(parameters)
        .allowDiagnosticMessages()
        .compileWithExpectedDiagnostics(
            diagnostics ->
                diagnostics
                    .assertOnlyWarnings()
                    .assertWarningsMatch(
                        diagnosticType(UnsupportedMainDexListUsageDiagnostic.class)))
        .apply(this::checkCompileResult)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private void checkCompileResult(R8TestCompileResult compileResult) throws Exception {
    Path out = temp.newFolder().toPath();
    compileResult.app.writeToDirectory(out, OutputMode.DexIndexed);
    Path classes = out.resolve("classes.dex");
    Path classes2 = out.resolve("classes2.dex");
    inspectMainDex(new CodeInspector(classes, compileResult.getProguardMap()));
    inspectSecondaryDex(new CodeInspector(classes2, compileResult.getProguardMap()));
  }

  private void inspectMainDex(CodeInspector inspector) {
    assertThat(inspector.clazz(TestClass.class), isPresent());
    assertThat(inspector.clazz(OtherTestClass.class), not(isPresent()));
  }

  private void inspectSecondaryDex(CodeInspector inspector) {
    assertThat(inspector.clazz(TestClass.class), not(isPresent()));
    assertThat(inspector.clazz(OtherTestClass.class), isPresent());
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println("Hello world!");
    }
  }

  static class OtherTestClass {}
}
