// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.container;

import static org.junit.Assert.assertArrayEquals;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.nio.file.Path;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DexContainerFormatBasicTest extends DexContainerFormatTestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  private static Path inputA;
  private static Path inputB;

  @BeforeClass
  public static void generateTestApplications() throws Throwable {
    // Build two applications in different packages both with required multidex due to number
    // of methods.
    inputA = getStaticTemp().getRoot().toPath().resolve("application_a.jar");
    inputB = getStaticTemp().getRoot().toPath().resolve("application_b.jar");

    generateApplication(inputA, "a", 10);
    generateApplication(inputB, "b", 10);
  }

  @Test
  public void testNonContainerD8() throws Exception {
    Path outputA =
        testForD8(Backend.DEX)
            .addProgramFiles(inputA)
            .setMinApi(AndroidApiLevel.L)
            .compile()
            .writeToZip();
    validateDex(outputA, 2, AndroidApiLevel.L.getDexVersion());

    Path outputB =
        testForD8(Backend.DEX)
            .addProgramFiles(inputB)
            .setMinApi(AndroidApiLevel.L)
            .compile()
            .writeToZip();
    validateDex(outputB, 2, AndroidApiLevel.L.getDexVersion());

    Path outputMerged =
        testForD8(Backend.DEX)
            .addProgramFiles(outputA, outputB)
            .setMinApi(AndroidApiLevel.L)
            .compile()
            .writeToZip();
    validateDex(outputMerged, 4, AndroidApiLevel.L.getDexVersion());
  }

  @Test
  public void testD8ExperimentSimpleMerge() throws Exception {
    Path outputFromDexing =
        testForD8(Backend.DEX)
            .addProgramFiles(inputA)
            .setMinApi(AndroidApiLevel.L)
            .addOptionsModification(
                options -> options.getTestingOptions().dexContainerExperiment = true)
            .compile()
            .writeToZip();
    validateSingleContainerDex(outputFromDexing);

    Path outputFromMerging =
        testForD8(Backend.DEX)
            .addProgramFiles(outputFromDexing)
            .addOptionsModification(
                options -> options.getTestingOptions().dexContainerExperiment = true)
            .compile()
            .writeToZip();
    validateSingleContainerDex(outputFromMerging);

    // Identical DEX after re-merging.
    assertArrayEquals(
        unzipContent(outputFromDexing).get(0), unzipContent(outputFromMerging).get(0));
  }

  @Test
  public void testD8ExperimentMoreMerge() throws Exception {
    Path outputA =
        testForD8(Backend.DEX)
            .addProgramFiles(inputA)
            .setMinApi(AndroidApiLevel.L)
            .addOptionsModification(
                options -> options.getTestingOptions().dexContainerExperiment = true)
            .compile()
            .writeToZip();
    validateSingleContainerDex(outputA);

    Path outputB =
        testForD8(Backend.DEX)
            .addProgramFiles(inputB)
            .setMinApi(AndroidApiLevel.L)
            .addOptionsModification(
                options -> options.getTestingOptions().dexContainerExperiment = true)
            .compile()
            .writeToZip();
    validateSingleContainerDex(outputB);

    Path outputBoth =
        testForD8(Backend.DEX)
            .addProgramFiles(inputA, inputB)
            .setMinApi(AndroidApiLevel.L)
            .addOptionsModification(
                options -> options.getTestingOptions().dexContainerExperiment = true)
            .compile()
            .writeToZip();
    validateSingleContainerDex(outputBoth);

    Path outputMerged =
        testForD8(Backend.DEX)
            .addProgramFiles(outputA, outputB)
            .addOptionsModification(
                options -> options.getTestingOptions().dexContainerExperiment = true)
            .compile()
            .writeToZip();
    validateSingleContainerDex(outputMerged);

    assertArrayEquals(unzipContent(outputBoth).get(0), unzipContent(outputMerged).get(0));
  }
}
