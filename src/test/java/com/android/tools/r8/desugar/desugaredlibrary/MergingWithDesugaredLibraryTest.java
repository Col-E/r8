// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.desugar.desugaredlibrary.jdktests.Jdk11DesugaredLibraryTestBase;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.nio.file.Path;
import java.util.ArrayList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MergingWithDesugaredLibraryTest extends Jdk11DesugaredLibraryTestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public MergingWithDesugaredLibraryTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testMergeDesugaredAndNonDesugared() throws Exception {
    try {
      testForD8()
          .addProgramFiles(buildPart1DesugaredLibrary(), buildPart2NoDesugaredLibrary())
          .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
          .setMinApi(parameters.getApiLevel())
          .enableCoreLibraryDesugaring(parameters.getApiLevel())
          .compile()
          .run(parameters.getRuntime(), Part1.class);
    } catch (AssertionError ae) {
      // TODO(b/154106502): Make it compile correctly or raise a proper warning.
      assertThat(ae.getMessage(), containsString("Out-of-order type ids"));
    }
  }

  @Test
  public void testMergeDesugaredAndClassFile() throws Exception {
    try {
      testForD8()
          .addProgramFiles(buildPart1DesugaredLibrary())
          .addProgramClasses(Part2.class)
          .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
          .setMinApi(parameters.getApiLevel())
          .enableCoreLibraryDesugaring(parameters.getApiLevel())
          .compile()
          .run(parameters.getRuntime(), Part1.class);
    } catch (AssertionError ae) {
      // TODO(b/154106502): Make it compile correctly or raise a proper warning.
      assertThat(ae.getMessage(), containsString("Out-of-order type ids"));
    }
  }

  private Path buildPart1DesugaredLibrary() throws Exception {
    return testForD8()
        .addProgramClasses(Part1.class)
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(parameters.getApiLevel())
        .compile()
        .writeToZip();
  }

  private Path buildPart2NoDesugaredLibrary() throws Exception {
    return testForD8()
        .addProgramClasses(Part2.class)
        .setMinApi(parameters.getApiLevel())
        .compile()
        .writeToZip();
  }

  @SuppressWarnings("RedundantOperationOnEmptyContainer")
  static class Part1 {
    public static void main(String[] args) {
      System.out.println(new ArrayList<>().stream().getClass().getSimpleName());
    }
  }

  @SuppressWarnings("RedundantOperationOnEmptyContainer")
  static class Part2 {
    public static void main(String[] args) {
      System.out.println(new ArrayList<>().stream().getClass().getSimpleName());
    }
  }
}
