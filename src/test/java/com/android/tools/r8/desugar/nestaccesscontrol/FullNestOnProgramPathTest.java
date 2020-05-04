// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.nestaccesscontrol;

import static com.android.tools.r8.desugar.nestaccesscontrol.NestAccessControlTestUtils.JAR;
import static com.android.tools.r8.desugar.nestaccesscontrol.NestAccessControlTestUtils.MAIN_CLASSES;
import static com.android.tools.r8.desugar.nestaccesscontrol.NestAccessControlTestUtils.NEST_IDS;
import static com.android.tools.r8.desugar.nestaccesscontrol.NestAccessControlTestUtils.NUMBER_OF_TEST_CLASSES;
import static com.android.tools.r8.desugar.nestaccesscontrol.NestAccessControlTestUtils.classesOfNest;
import static com.android.tools.r8.desugar.nestaccesscontrol.NestAccessControlTestUtils.getExpectedResult;
import static com.android.tools.r8.desugar.nestaccesscontrol.NestAccessControlTestUtils.getMainClass;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class FullNestOnProgramPathTest extends TestBase {

  public FullNestOnProgramPathTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK11)
        .withDexRuntimes()
        .withAllApiLevels()
        .build();
  }

  public static Function<AndroidApiLevel, D8TestCompileResult> d8CompilationResult =
      memoizeFunction(FullNestOnProgramPathTest::compileAllNestsD8);

  public static BiFunction<Backend, AndroidApiLevel, R8TestCompileResult> r8CompilationResult =
      memoizeBiFunction(FullNestOnProgramPathTest::compileAllNestsR8);

  // All Nests tests compile all the nests into dex,
  // and run the main class from this dex
  @Test
  @Ignore("b/141075451")
  public void testAllNestsJavaAndD8() throws Exception {
    for (String nestID : NEST_IDS) {
      if (parameters.isCfRuntime()) {
        testForJvm()
            .addProgramFiles(JAR)
            .run(parameters.getRuntime(), getMainClass(nestID))
            .assertSuccessWithOutput(getExpectedResult(nestID));
      } else {
        assert parameters.isDexRuntime();
        d8CompilationResult
            .apply(parameters.getApiLevel())
            .run(parameters.getRuntime(), getMainClass(nestID))
            .assertSuccessWithOutput(getExpectedResult(nestID));
      }
    }
  }

  @Test
  @Ignore("b/141075451")
  public void testAllNestsR8() throws Exception {
    for (String nestID : NEST_IDS) {
      R8TestRunResult result =
          r8CompilationResult
              .apply(parameters.getBackend(), parameters.getApiLevel())
              .run(parameters.getRuntime(), getMainClass(nestID));
      result.assertSuccessWithOutput(getExpectedResult(nestID));
      if (parameters.isCfRuntime()) {
        result.inspect(FullNestOnProgramPathTest::checkNestMateAttributes);
      }
    }
  }

  // Single Nest test compile only the nest into dex,
  // then run the main class from this dex.
  @Test
  public void testSingleNestR8() throws Exception {
    for (String nestID : NEST_IDS) {
      testForR8(parameters.getBackend())
          .noTreeShaking()
          .noMinification()
          .addKeepAllAttributes()
          .setMinApi(parameters.getApiLevel())
          .addProgramFiles(classesOfNest(nestID))
          .addOptionsModification(
              options -> {
                options.enableNestBasedAccessDesugaring = true;
                options.enableNestReduction = false;
              })
          .compile()
          .run(parameters.getRuntime(), getMainClass(nestID))
          .assertSuccessWithOutput(getExpectedResult(nestID));
    }
  }

  @Test
  public void testSingleNestD8() throws Exception {
    Assume.assumeTrue(parameters.isDexRuntime());
    for (String nestID : NEST_IDS) {
      testForD8()
          .setMinApi(parameters.getApiLevel())
          .addProgramFiles(classesOfNest(nestID))
          .addOptionsModification(options -> options.enableNestBasedAccessDesugaring = true)
          .compile()
          .run(parameters.getRuntime(), getMainClass(nestID))
          .assertSuccessWithOutput(getExpectedResult(nestID));
    }
  }

  private static D8TestCompileResult compileAllNestsD8(AndroidApiLevel minApi)
      throws CompilationFailedException {
    return testForD8(getStaticTemp())
        .addProgramFiles(JAR)
        .addOptionsModification(
            options -> {
              options.enableNestBasedAccessDesugaring = true;
            })
        .setMinApi(minApi)
        .compile();
  }

  private static R8TestCompileResult compileAllNestsR8(Backend backend, AndroidApiLevel minApi)
      throws CompilationFailedException {
    return testForR8(getStaticTemp(), backend)
        .noTreeShaking()
        .noMinification()
        .addKeepAllAttributes()
        .addOptionsModification(
            options -> {
              options.enableNestBasedAccessDesugaring = true;
              options.enableNestReduction = false;
            })
        .addProgramFiles(JAR)
        .setMinApi(minApi)
        .compile();
  }

  private static void checkNestMateAttributes(CodeInspector inspector) {
    // Interface method desugaring may add extra classes
    assertTrue(NUMBER_OF_TEST_CLASSES <= inspector.allClasses().size());
    ImmutableList<String> outerClassNames = MAIN_CLASSES.values().asList();
    ImmutableList<String> nonNestClasses =
        ImmutableList.of("NeverInline", "OutsideInliningNoAccess", "OutsideInliningWithAccess");
    inspector.forAllClasses(
        classSubject -> {
          DexClass dexClass = classSubject.getDexProgramClass();
          if (!nonNestClasses.contains(dexClass.type.getName())) {
            assertTrue(dexClass.isInANest());
            if (outerClassNames.contains(dexClass.type.getName())) {
              assertNull(dexClass.getNestHostClassAttribute());
              assertFalse(dexClass.getNestMembersClassAttributes().isEmpty());
            } else {
              assertTrue(dexClass.getNestMembersClassAttributes().isEmpty());
              assertTrue(
                  outerClassNames.contains(
                      dexClass.getNestHostClassAttribute().getNestHost().getName()));
            }
          }
        });
  }
}
