// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IterateTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final boolean shrinkDesugaredLibrary;
  private static final String EXPECTED_OUTPUT = StringUtils.lines("1");

  @Parameters(name = "{1}, shrinkDesugaredLibrary: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(),
        getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build());
  }

  public IterateTest(boolean shrinkDesugaredLibrary, TestParameters parameters) {
    this.shrinkDesugaredLibrary = shrinkDesugaredLibrary;
    this.parameters = parameters;
  }

  @Test
  public void testIterateD8Cf() throws Exception {
    // Only test without shrinking desugared library.
    Assume.assumeFalse(shrinkDesugaredLibrary);
    // Use D8 to desugar with Java classfile output.
    Path jar =
        testForD8(Backend.CF)
            .addInnerClasses(IterateTest.class)
            .setMinApi(parameters.getApiLevel())
            .enableCoreLibraryDesugaring(parameters.getApiLevel())
            .compile()
            .writeToZip();

    if (parameters.getRuntime().isDex()) {
      // Convert to DEX without desugaring and run.
      testForD8()
          .addProgramFiles(jar)
          .setMinApi(parameters.getApiLevel())
          .disableDesugaring()
          .compile()
          .addRunClasspathFiles(buildDesugaredLibrary(parameters.getApiLevel()))
          .run(parameters.getRuntime(), Main.class)
          .assertSuccessWithOutput(EXPECTED_OUTPUT);
    } else {
      // Run on the JVM with desugared library on classpath.
      testForJvm()
          .addProgramFiles(jar)
          .addRunClasspathFiles(buildDesugaredLibraryClassFile(parameters.getApiLevel()))
          .run(parameters.getRuntime(), Main.class)
          .assertSuccessWithOutput(EXPECTED_OUTPUT);
    }
  }

  @Test
  public void testIterate() throws Exception {
    if (parameters.isCfRuntime()) {
      testForJvm()
          .addInnerClasses(IterateTest.class)
          .run(parameters.getRuntime(), Main.class)
          .assertSuccessWithOutput(EXPECTED_OUTPUT);
      return;
    }
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForD8()
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .addInnerClasses(IterateTest.class)
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  static class Main {

    public static void main(String[] args) {
      IntStream iterate = IntStream.iterate(1, x -> x + 3);
      System.out.println(iterate.findFirst().getAsInt());
    }
  }
}
