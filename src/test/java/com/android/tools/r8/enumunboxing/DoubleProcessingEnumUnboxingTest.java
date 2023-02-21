// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.enumunboxing;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.enumunboxing.examplelib1.JavaLibrary1;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

// In this test enum unboxing is performed once cf to cf then once cf to dex. The enum unboxing
// utility class is required in both cases, and R8 should not conflict with multiple synthesized
// classes.
@RunWith(Parameterized.class)
public class DoubleProcessingEnumUnboxingTest extends EnumUnboxingTestBase {
  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;
  private final boolean minification;

  @Parameters(name = "{0} valueOpt: {1} keep: {2} minif: {3}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        BooleanUtils.values(),
        getAllEnumKeepRules(),
        BooleanUtils.values());
  }

  public DoubleProcessingEnumUnboxingTest(
      TestParameters parameters,
      boolean enumValueOptimization,
      EnumKeepRules enumKeepRules,
      boolean minification) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
    this.minification = minification;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    // Compile the lib cf to cf.
    Path javaLibShrunk =
        testForR8(Backend.CF)
            .addProgramClasses(JavaLibrary1.class, JavaLibrary1.LibEnum1.class)
            .addKeepMethodRules(
                Reference.methodFromMethod(JavaLibrary1.class.getDeclaredMethod("libCall")))
            .addKeepRules(enumKeepRules.getKeepRules())
            .enableNeverClassInliningAnnotations()
            .enableInliningAnnotations()
            .minification(minification)
            .compile()
            .writeToZip();
    // Compile the app with the lib.
    testForR8(parameters.getBackend())
        .addProgramClasses(App.class, App.AppEnum.class)
        .addProgramFiles(javaLibShrunk)
        .addKeepMainRule(App.class)
        .addKeepRules(enumKeepRules.getKeepRules())
        .addEnumUnboxingInspector(inspector -> inspector.assertUnboxed(App.AppEnum.class))
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
        .setMinApi(parameters)
        .compile()
        .inspect(this::assertSharedUtilityClassPresent)
        .run(parameters.getRuntime(), App.class)
        .assertSuccess()
        .inspectStdOut(this::assertLines2By2Correct);
  }

  private void assertSharedUtilityClassPresent(CodeInspector codeInspector) {
    assertTrue(
        codeInspector.allClasses().stream()
            .anyMatch(
                clazz ->
                    SyntheticItemsTestUtils.isEnumUnboxingSharedUtilityClass(
                        clazz.getOriginalReference())));
  }

  static class App {
    @NeverClassInline
    enum AppEnum {
      A,
      B
    }

    @NeverInline
    static AppEnum getEnum() {
      return System.currentTimeMillis() > 0 ? AppEnum.A : AppEnum.B;
    }

    public static void main(String[] args) {
      System.out.println(getEnum().ordinal());
      System.out.println(0);
      JavaLibrary1.libCall();
    }
  }
}
