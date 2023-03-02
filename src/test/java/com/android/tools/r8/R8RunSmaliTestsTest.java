// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.TestDescriptionWatcher;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class R8RunSmaliTestsTest extends TestBase {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Parameters(name = "{0}: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(), tests.keySet());
  }

  private static final String SMALI_DIR = ToolHelper.SMALI_BUILD_DIR;

  private static Map<String, String> tests;

  static {
    ImmutableMap.Builder<String, String> testsBuilder = ImmutableMap.builder();
    testsBuilder
        .put(
            "arithmetic",
            StringUtils.lines(
                "-1", "3", "2", "3", "3.0", "1", "0", "-131580", "-131580", "2", "4", "-2"))
        .put(
            "controlflow",
            StringUtils.lines("2", "1", "2", "1", "2", "1", "2", "1", "2", "1", "2", "1", "2"))
        .put("fibonacci", StringUtils.lines("55", "55", "55", "55"))
        .put("fill-array-data", "[1, 2, 3][4, 5, 6]")
        .put("filled-new-array", "[1, 2, 3][4, 5, 6][1, 2, 3, 4, 5, 6][6, 5, 4, 3, 2, 1]")
        .put("packed-switch", "12345")
        .put("sparse-switch", "12345")
        .put("unreachable-code-1", "777")
        .put(
            "multiple-returns",
            StringUtils.lines("TFtf", "1", "4611686018427387904", "true", "false"))
        .put("try-catch", "")
        .put("phi-removal-regression", StringUtils.lines("returnBoolean"))
        .put(
            "overlapping-long-registers",
            StringUtils.lines("-9151314442816847872", "-9151314442816319488"))
        .put(
            "type-confusion-regression",
            StringUtils.lines("java.lang.RuntimeException: Test.<init>()"))
        .put(
            "type-confusion-regression2",
            StringUtils.lines("java.lang.NullPointerException: Attempt to read from null array"))
        .put(
            "type-confusion-regression3",
            StringUtils.lines(
                "java.lang.NullPointerException: Attempt to read from field 'byte[] Test.a'"
                    + " on a null object reference"))
        .put("type-confusion-regression4", "")
        .put(
            "type-confusion-regression5", StringUtils.lines("java.lang.RuntimeException: getId()I"))
        .put("chain-of-loops", StringUtils.lines("java.lang.RuntimeException: f(II)"))
        .put("new-instance-and-init", StringUtils.lines("Test(0)", "Test(0)", "Test(0)"))
        .put(
            "bad-codegen",
            StringUtils.lines(
                "java.lang.NullPointerException: Attempt to read from field "
                    + "'Test Test.a' on a null object reference"))
        .put(
            "merge-blocks-regression",
            StringUtils.lines(
                "java.lang.NullPointerException: Attempt to invoke virtual"
                    + " method 'Test Test.bW_()' on a null object reference"))
        .put("self-is-catch-block", StringUtils.lines("100", "-1"))
        .put("infinite-loop", "")
        .put(
            "regression/33336471",
            StringUtils.lines(
                "START", "0", "2", "LOOP", "1", "2", "LOOP", "2", "2", "DONE", "START", "0", "2",
                "LOOP", "1", "2", "LOOP", "2", "2", "DONE"))
        .put("regression/33846227", "")
        .put("illegal-invokes", StringUtils.lines("ICCE", "ICCE"))
        .build();
    tests = testsBuilder.build();
  }

  private static Map<String, Set<String>> missingClasses =
      ImmutableMap.of(
          "try-catch", ImmutableSet.of("test.X"),
          "type-confusion-regression5", ImmutableSet.of("jok", "jol"),
          "bad-codegen", ImmutableSet.of("java.util.LTest"));

  // Tests where the original smali code fails on Art, but runs after R8 processing.
  private static final Map<DexVm.Version, List<String>> originalFailingOnArtVersions =
      ImmutableMap.of(
          Version.V5_1_1,
              ImmutableList.of(
                  // Smali code contains an empty switch payload.
                  "sparse-switch", "regression/33846227"),
          Version.V4_4_4,
              ImmutableList.of(
                  // Smali code contains an empty switch payload.
                  "sparse-switch", "regression/33846227"),
          Version.V4_0_4,
              ImmutableList.of(
                  // Smali code contains an empty switch payload.
                  "sparse-switch", "regression/33846227"));

  // Tests where the output has a different output than the original on certain VMs.
  private static final Map<DexVm.Version, Map<String, String>> customProcessedOutputExpectation =
      ImmutableMap.of(
          Version.V4_4_4,
          ImmutableMap.of(
              "bad-codegen", "java.lang.NullPointerException\n",
              "type-confusion-regression2", "java.lang.NullPointerException\n",
              "type-confusion-regression3", "java.lang.NullPointerException\n",
              "merge-blocks-regression", "java.lang.NullPointerException\n"),
          Version.V4_0_4,
          ImmutableMap.of(
              "bad-codegen", "java.lang.NullPointerException\n",
              "type-confusion-regression2", "java.lang.NullPointerException\n",
              "type-confusion-regression3", "java.lang.NullPointerException\n",
              "merge-blocks-regression", "java.lang.NullPointerException\n"),
          Version.V13_0_0,
          ImmutableMap.of(
              "bad-codegen",
              StringUtils.lines(
                  "java.lang.NullPointerException: Attempt to read from field 'Test Test.a'"
                      + " on a null object reference in method 'Test TestObject.a(Test,"
                      + " Test, Test, Test, boolean)'"),
              "type-confusion-regression3",
              StringUtils.lines(
                  "java.lang.NullPointerException: Attempt to read from field 'byte[]"
                      + " Test.a' on a null object reference in method 'int"
                      + " TestObject.a(Test, Test)'")),
          Version.V14_0_0,
          ImmutableMap.of(
              "bad-codegen",
                  StringUtils.lines(
                      "java.lang.NullPointerException: Attempt to read from field 'Test Test.a'"
                          + " on a null object reference in method 'Test TestObject.a(Test,"
                          + " Test, Test, Test, boolean)'"),
              "type-confusion-regression3",
                  StringUtils.lines(
                      "java.lang.NullPointerException: Attempt to read from field 'byte[]"
                          + " Test.a' on a null object reference in method 'int"
                          + " TestObject.a(Test, Test)'")));

  // Tests where the input fails with a verification error on Dalvik instead of the
  // expected runtime exception.
  private static final Map<DexVm.Version, List<String>> dalvikVerificationErrors =
      ImmutableMap.of(
          Version.V4_4_4,
              ImmutableList.of(
                  // The invokes are in fact invalid, but the test expects the current Art behavior
                  // of throwing an IncompatibleClassChange exception. Dalvik fails to verify.
                  "illegal-invokes"),
          Version.V4_0_4,
              ImmutableList.of(
                  // The invokes are in fact invalid, but the test expects the current Art behavior
                  // of throwing an IncompatibleClassChange exception. Dalvik fails to verify.
                  "illegal-invokes"));

  private Set<String> failingOnX8 = ImmutableSet.of(
      // Contains use of register as both an int and a float.
      "regression/33336471"
  );

  @Rule
  public TestDescriptionWatcher watcher = new TestDescriptionWatcher();

  private final TestParameters parameters;
  private final String directoryName;
  private final String dexFileName;
  private final String expectedOutput;

  public R8RunSmaliTestsTest(TestParameters parameters, String name) {
    this.parameters = parameters;
    String expectedOutput = tests.get(name);
    if (customProcessedOutputExpectation.containsKey(parameters.asDexRuntime().getVersion())
        && customProcessedOutputExpectation
            .get(parameters.asDexRuntime().getVersion())
            .containsKey(name)) {
      // If the original and the processed code have different expected output, only run
      // the code produced by R8.
      expectedOutput =
          customProcessedOutputExpectation.get(parameters.asDexRuntime().getVersion()).get(name);
    }
    this.directoryName = name;
    this.dexFileName = name.substring(name.lastIndexOf('/') + 1) + ".dex";
    this.expectedOutput = expectedOutput;
  }

  @Test
  public void SmaliTest() throws Exception {
    Path originalDexFile = Paths.get(SMALI_DIR, directoryName, dexFileName);
    // Path outputPath = temp.getRoot().toPath().resolve("classes.dex");

    if (failingOnX8.contains(directoryName)) {
      thrown.expect(CompilationFailedException.class);
    }

    Version version = parameters.asDexRuntime().getVersion();
    boolean dalvikVerificationError =
        dalvikVerificationErrors.containsKey(version)
            && dalvikVerificationErrors.get(version).contains(directoryName);
    boolean originalFailing =
        (originalFailingOnArtVersions.containsKey(version)
            && originalFailingOnArtVersions.get(version).contains(directoryName));
    testForR8(parameters.getBackend())
        .addKeepAllClassesRule()
        .addProgramDexFileData(Files.readAllBytes(originalDexFile))
        .addDontWarn(missingClasses.getOrDefault(directoryName, Collections.emptySet()))
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), "Test")
        .applyIf(
            dalvikVerificationError,
            r -> r.assertFailureWithErrorThatThrows(VerifyError.class),
            r -> r.assertSuccessWithOutput(expectedOutput));

    // Also run the original DEX if possible.
    if (!dalvikVerificationError && !originalFailing) {
      String originalOutput =
          ToolHelper.runArtNoVerificationErrors(
              ImmutableList.of(originalDexFile.toString()),
              "Test",
              null,
              parameters.getRuntime().asDex().getVm());
      assertEquals(expectedOutput, originalOutput);
    }
  }
}
