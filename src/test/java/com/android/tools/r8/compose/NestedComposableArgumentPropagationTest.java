// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.compose;

import static com.google.common.base.Predicates.alwaysTrue;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.ZipUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.HorizontallyMergedClassesInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.codeinspector.MinificationInspector;
import com.android.tools.r8.utils.codeinspector.RepackagingInspector;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumMap;
import java.util.function.BiFunction;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NestedComposableArgumentPropagationTest extends TestBase {

  enum ComposableFunction {
    A,
    B,
    C
  }

  static class CodeStats {

    final int numberOfIfInstructions;

    CodeStats(MethodSubject methodSubject) {
      this.numberOfIfInstructions =
          (int) methodSubject.streamInstructions().filter(InstructionSubject::isIf).count();
    }
  }

  private static Path dump;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @BeforeClass
  public static void setup() throws IOException {
    dump = getStaticTemp().newFolder().toPath();
    ZipUtils.unzip(
        Paths.get(
            ToolHelper.THIRD_PARTY_DIR,
            "opensource-apps/compose-examples/changed-bitwise-value-propagation/dump.zip"),
        dump);
  }

  public NestedComposableArgumentPropagationTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void test() throws Exception {
    EnumMap<ComposableFunction, CodeStats> defaultCodeStats = build(false);
    EnumMap<ComposableFunction, CodeStats> optimizedCodeStats = build(true);
    for (ComposableFunction composableFunction : ComposableFunction.values()) {
      CodeStats defaultCodeStatsForFunction = defaultCodeStats.get(composableFunction);
      CodeStats optimizedCodeStatsForFunction = optimizedCodeStats.get(composableFunction);
      assertTrue(
          composableFunction
              + ": "
              + defaultCodeStatsForFunction.numberOfIfInstructions
              + " vs "
              + optimizedCodeStatsForFunction.numberOfIfInstructions,
          defaultCodeStatsForFunction.numberOfIfInstructions
              > optimizedCodeStatsForFunction.numberOfIfInstructions);
    }
  }

  private EnumMap<ComposableFunction, CodeStats> build(boolean enableComposeOptimizations)
      throws Exception {
    Box<ClassReference> mainActivityKtClassReference =
        new Box<>(Reference.classFromTypeName("com.example.MainActivityKt"));
    R8TestCompileResult compileResult =
        testForR8(Backend.DEX)
            .addProgramFiles(dump.resolve("program.jar"))
            .addClasspathFiles(dump.resolve("classpath.jar"))
            .addLibraryFiles(dump.resolve("library.jar"))
            .addKeepRuleFiles(dump.resolve("proguard.config"))
            .addHorizontallyMergedClassesInspector(
                updateMainActivityKt(
                    HorizontallyMergedClassesInspector::getTarget,
                    mainActivityKtClassReference,
                    false),
                alwaysTrue())
            .addRepackagingInspector(
                updateMainActivityKt(
                    RepackagingInspector::getTarget, mainActivityKtClassReference, true))
            .addMinificationInspector(
                updateMainActivityKt(
                    MinificationInspector::getTarget, mainActivityKtClassReference, true))
            .addOptionsModification(
                options -> {
                  options.getOpenClosedInterfacesOptions().suppressAllOpenInterfaces();
                  options.testing.enableComposableOptimizationPass = enableComposeOptimizations;
                  options.testing.modelUnknownChangedAndDefaultArgumentsToComposableFunctions =
                      enableComposeOptimizations;
                })
            .setMinApi(AndroidApiLevel.N)
            .allowDiagnosticMessages()
            .allowUnnecessaryDontWarnWildcards()
            .allowUnusedDontWarnPatterns()
            .allowUnusedProguardConfigurationRules()
            .compile();
    return createCodeStats(compileResult.inspector().clazz(mainActivityKtClassReference.get()));
  }

  private EnumMap<ComposableFunction, CodeStats> createCodeStats(
      ClassSubject mainActivityKtClassSubject) {
    EnumMap<ComposableFunction, CodeStats> result = new EnumMap<>(ComposableFunction.class);
    result.put(
        ComposableFunction.A,
        new CodeStats(mainActivityKtClassSubject.uniqueMethodWithOriginalName("A")));
    result.put(
        ComposableFunction.B,
        new CodeStats(mainActivityKtClassSubject.uniqueMethodWithOriginalName("B")));
    result.put(
        ComposableFunction.C,
        new CodeStats(mainActivityKtClassSubject.uniqueMethodWithOriginalName("C")));
    return result;
  }

  private static <T> ThrowableConsumer<T> updateMainActivityKt(
      BiFunction<T, ClassReference, ClassReference> targetFn,
      Box<ClassReference> mainActivityKtClassReference,
      boolean failIfUnchanged) {
    return inspector -> {
      ClassReference targetClass = targetFn.apply(inspector, mainActivityKtClassReference.get());
      if (failIfUnchanged) {
        assertNotEquals(mainActivityKtClassReference.get(), targetClass);
      }
      mainActivityKtClassReference.set(targetClass);
    };
  }
}
