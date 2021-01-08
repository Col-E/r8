// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import static com.android.tools.r8.shaking.ProguardConfigurationParser.FLATTEN_PACKAGE_HIERARCHY;
import static com.android.tools.r8.shaking.ProguardConfigurationParser.REPACKAGE_CLASSES;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RepackageWithMainDexTracingTest extends RepackageTestBase {

  @Parameters(name = "{1}, kind: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        ImmutableList.of(FLATTEN_PACKAGE_HIERARCHY, REPACKAGE_CLASSES),
        getTestParameters()
            .withDexRuntimes()
            .withApiLevelsEndingAtExcluding(apiLevelWithNativeMultiDexSupport())
            .build());
  }

  public RepackageWithMainDexTracingTest(
      String flattenPackageHierarchyOrRepackageClasses, TestParameters parameters) {
    super(flattenPackageHierarchyOrRepackageClasses, parameters);
  }

  @Test
  public void testR8() throws Exception {
    Box<String> r8MainDexList = new Box<>();
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepClassRulesWithAllowObfuscation(Other.class)
        .addMainDexClassRules(Main.class)
        .addOptionsModification(options -> options.minimalMainDex = true)
        .enableNeverClassInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters.getApiLevel())
        .setMainDexListConsumer(ToolHelper.consumeString(r8MainDexList::set))
        .apply(this::configureRepackaging)
        .compile()
        .apply(result -> checkCompileResult(result, r8MainDexList.get()))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("main dex");
  }

  private void checkCompileResult(R8TestCompileResult compileResult, String mainDexList)
      throws Exception {
    Path out = temp.newFolder().toPath();
    compileResult.app.writeToDirectory(out, OutputMode.DexIndexed);
    Path classes = out.resolve("classes.dex");
    Path classes2 = out.resolve("classes2.dex");
    inspectMainDex(new CodeInspector(classes, compileResult.getProguardMap()), mainDexList);
    inspectSecondaryDex(new CodeInspector(classes2, compileResult.getProguardMap()));
  }

  private void inspectMainDex(CodeInspector inspector, String mainDexList) {
    assertThat(inspector.clazz(Main.class), isPresentAndNotRenamed());
    assertThat(Main.class, isNotRepackaged(inspector));
    assertThat(inspector.clazz(A.class), isPresentAndRenamed());
    assertThat(A.class, isRepackaged(inspector));
    List<String> mainDexTypeNames = StringUtils.splitLines(mainDexList);
    assertEquals(2, mainDexTypeNames.size());
    assertEquals(
        inspector.clazz(Main.class).getFinalBinaryName(),
        mainDexTypeNames.get(0).replace(".class", ""));
    assertEquals(
        inspector.clazz(A.class).getFinalBinaryName(),
        mainDexTypeNames.get(1).replace(".class", ""));
    assertThat(inspector.clazz(Other.class), not(isPresent()));
  }

  private void inspectSecondaryDex(CodeInspector inspector) {
    assertThat(inspector.clazz(Main.class), not(isPresent()));
    assertThat(inspector.clazz(A.class), not(isPresent()));
    assertThat(inspector.clazz(Other.class), isPresentAndRenamed());
    assertThat(Other.class, isRepackaged(inspector));
  }

  @NoHorizontalClassMerging
  public static class Other {}

  @NeverClassInline
  public static class A {
    public A() {
      System.out.println("main dex");
    }
  }

  public static class Main {
    public static void main(String[] args) {
      A a = new A();
    }
  }
}
