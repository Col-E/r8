// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.container;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DexContainerFormatEmptyTest extends DexContainerFormatTestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void testNonContainerD8() throws Exception {
    Path outputA = testForD8(Backend.DEX).setMinApi(AndroidApiLevel.L).compile().writeToZip();
    assertEquals(0, unzipContent(outputA).size());

    Path outputB = testForD8(Backend.DEX).setMinApi(AndroidApiLevel.L).compile().writeToZip();
    assertEquals(0, unzipContent(outputB).size());

    Path outputMerged =
        testForD8(Backend.DEX)
            .addProgramFiles(outputA, outputB)
            .setMinApi(AndroidApiLevel.L)
            .compile()
            .writeToZip();
    assertEquals(0, unzipContent(outputMerged).size());
  }

  @Test
  public void testD8Experiment() throws Exception {
    Path outputFromDexing =
        testForD8(Backend.DEX)
            .setMinApi(AndroidApiLevel.L)
            .addOptionsModification(
                options -> options.getTestingOptions().dexContainerExperiment = true)
            .compile()
            .writeToZip();
    assertEquals(0, unzipContent(outputFromDexing).size());
  }
}
