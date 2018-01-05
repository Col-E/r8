// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.OutputMode;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class R8RunSmaliTestsTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private static final String SMALI_DIR = ToolHelper.SMALI_BUILD_DIR;

  // Tests where the original smali code fails on Art, but runs after R8 processing.
  private static Map<DexVm.Version, List<String>> originalFailingOnArtVersions = ImmutableMap.of(
      Version.V5_1_1, ImmutableList.of(
          // Smali code contains an empty switch payload.
          "sparse-switch",
          "regression/33846227"
      ),
      Version.V4_4_4, ImmutableList.of(
          // Smali code contains an empty switch payload.
          "sparse-switch",
          "regression/33846227"
      ),
      Version.V4_0_4, ImmutableList.of(
          // Smali code contains an empty switch payload.
          "sparse-switch",
          "regression/33846227"
      )
  );

  // Tests where the output has a different output than the original on certain VMs.
  private static Map<DexVm.Version, Map<String, String>> customProcessedOutputExpectation =
      ImmutableMap.of(
          Version.V4_4_4, ImmutableMap.of(
              "bad-codegen", "java.lang.NullPointerException\n",
              "type-confusion-regression2", "java.lang.NullPointerException\n",
              "type-confusion-regression3", "java.lang.NullPointerException\n",
              "merge-blocks-regression", "java.lang.NullPointerException\n"
          ),
          Version.V4_0_4, ImmutableMap.of(
              "bad-codegen", "java.lang.NullPointerException\n",
              "type-confusion-regression2", "java.lang.NullPointerException\n",
              "type-confusion-regression3", "java.lang.NullPointerException\n",
              "merge-blocks-regression", "java.lang.NullPointerException\n"
          )
      );

  // Tests where the input fails with a verification error on Dalvik instead of the
  // expected runtime exception.
  private static Map<DexVm.Version, List<String>> dalvikVerificationError = ImmutableMap.of(
      Version.V4_4_4, ImmutableList.of(
          // The invokes are in fact invalid, but the test expects the current Art behavior
          // of throwing an IncompatibleClassChange exception. Dalvik fails to verify.
          "illegal-invokes"
      ),
      Version.V4_0_4, ImmutableList.of(
          // The invokes are in fact invalid, but the test expects the current Art behavior
          // of throwing an IncompatibleClassChange exception. Dalvik fails to verify.
          "illegal-invokes"
      )
  );

  // Tests where the original smali code runs on Art, but fails after R8 processing
  private static Map<String, List<String>> failingOnArtVersions = ImmutableMap.of(
      // This list is currently empty!
  );

  @Rule
  public TemporaryFolder temp = ToolHelper.getTemporaryFolderForTest();

  @Parameters(name = "{0}")
  public static Collection<String[]> data() {
    return Arrays.asList(new String[][]{
        {"arithmetic",
            StringUtils.lines("-1", "3", "2", "3", "3.0", "1", "0", "-131580", "-131580", "2", "4",
                "-2")},
        {"controlflow",
            StringUtils.lines("2", "1", "2", "1", "2", "1", "2", "1", "2", "1", "2", "1", "2")},
        {"fibonacci", StringUtils.lines("55", "55", "55", "55")},
        {"fill-array-data", "[1, 2, 3][4, 5, 6]"},
        {"filled-new-array", "[1, 2, 3][4, 5, 6][1, 2, 3, 4, 5, 6][6, 5, 4, 3, 2, 1]"},
        {"packed-switch", "12345"},
        {"sparse-switch", "12345"},
        {"unreachable-code-1", "777"},
        {"multiple-returns",
            StringUtils.lines("TFtf", "1", "4611686018427387904", "true", "false")},
        {"try-catch", ""},
        {"phi-removal-regression", StringUtils.lines("returnBoolean")},
        {"overlapping-long-registers",
            StringUtils.lines("-9151314442816847872", "-9151314442816319488")},
        {"type-confusion-regression",
            StringUtils.lines("java.lang.RuntimeException: Test.<init>()")},
        {"type-confusion-regression2",
            StringUtils.lines("java.lang.NullPointerException: Attempt to read from null array")},
        {"type-confusion-regression3",
            StringUtils.lines(
                "java.lang.NullPointerException: Attempt to read from field 'byte[] Test.a'" +
                    " on a null object reference")},
        {"type-confusion-regression4", ""},
        {"type-confusion-regression5", StringUtils.lines("java.lang.RuntimeException: getId()I")},
        {"chain-of-loops", StringUtils.lines("java.lang.RuntimeException: f(II)")},
        {"new-instance-and-init", StringUtils.lines("Test(0)", "Test(0)", "Test(0)")},
        {"bad-codegen",
            StringUtils.lines("java.lang.NullPointerException: Attempt to read from field " +
                "'Test Test.a' on a null object reference")},
        {"merge-blocks-regression",
            StringUtils.lines("java.lang.NullPointerException: Attempt to invoke virtual"
                + " method 'Test Test.bW_()' on a null object reference")},
        {"self-is-catch-block", StringUtils.lines("100", "-1")},
        {"infinite-loop", ""},
        {"regression/33336471",
            StringUtils.lines("START", "0", "2", "LOOP", "1", "2", "LOOP", "2", "2", "DONE",
                "START", "0", "2", "LOOP", "1", "2", "LOOP", "2", "2", "DONE")},
        {"regression/33846227", ""},
        {"illegal-invokes", StringUtils.lines("ICCE", "ICCE")},
    });
  }

  private String directoryName;
  private String dexFileName;
  private String expectedOutput;

  public R8RunSmaliTestsTest(String name, String expectedOutput) {
    this.directoryName = name;
    this.dexFileName = name.substring(name.lastIndexOf('/') + 1) + ".dex";
    this.expectedOutput = expectedOutput;
  }

  @Test
  public void SmaliTest() throws Exception {
    Path originalDexFile = Paths.get(SMALI_DIR, directoryName, dexFileName);
    String outputPath = temp.getRoot().getCanonicalPath();
    R8Command.Builder builder = R8Command.builder()
            .addLibraryFiles(Paths.get(ToolHelper.getDefaultAndroidJar()))
            .setOutput(Paths.get(outputPath), OutputMode.DexIndexed);
    ToolHelper.getAppBuilder(builder).addProgramFiles(originalDexFile);
    R8.run(builder.build());

    if (!ToolHelper.artSupported()) {
      return;
    }

    String mainClass = "Test";
    String generated = outputPath + "/classes.dex";
    String output = "";

    DexVm.Version dexVmVersion = ToolHelper.getDexVm().getVersion();
    if (dalvikVerificationError.containsKey(dexVmVersion)
        && dalvikVerificationError.get(dexVmVersion).contains(directoryName)) {
      try {
        ToolHelper.runArtNoVerificationErrors(generated, mainClass);
      } catch (AssertionError e) {
        assert e.toString().contains("VerifyError");
      }
      return;
    } else if (originalFailingOnArtVersions.containsKey(dexVmVersion)
        && originalFailingOnArtVersions.get(dexVmVersion).contains(directoryName)) {
      // If the original smali code fails on the target VM, only run the code produced by R8.
      output = ToolHelper.runArtNoVerificationErrors(generated, mainClass);
    } else if (customProcessedOutputExpectation.containsKey(dexVmVersion)
        && customProcessedOutputExpectation.get(dexVmVersion).containsKey(directoryName)) {
      // If the original and the processed code have different expected output, only run
      // the code produced by R8.
      expectedOutput =
          customProcessedOutputExpectation.get(dexVmVersion).get(directoryName);
      output = ToolHelper.runArtNoVerificationErrors(generated, mainClass);
    } else {
      if (failingOnArtVersions.containsKey(dexVmVersion)
          && failingOnArtVersions.get(dexVmVersion).contains(directoryName)) {
        thrown.expect(Throwable.class);
      }
      output =
          ToolHelper
              .checkArtOutputIdentical(originalDexFile.toString(), generated, mainClass, null);
    }
    assertEquals(expectedOutput, output);
  }
}
