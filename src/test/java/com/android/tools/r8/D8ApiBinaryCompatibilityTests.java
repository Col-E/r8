// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.utils.FileUtils.JAR_EXTENSION;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class D8ApiBinaryCompatibilityTests extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public D8ApiBinaryCompatibilityTests(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void testCompatibility() throws IOException {
    Path jar = ToolHelper.API_SAMPLE_JAR;
    String main = "com.android.tools.apiusagesample.D8ApiUsageSample";
    int minApiLevel = AndroidApiLevel.K.getLevel();

    Path lib1 =
        Paths.get(
            ToolHelper.EXAMPLES_ANDROID_O_BUILD_DIR,
            "desugaringwithmissingclasslib1" + JAR_EXTENSION);
    Path lib2 =
        Paths.get(
            ToolHelper.EXAMPLES_ANDROID_O_BUILD_DIR,
            "desugaringwithmissingclasslib2" + JAR_EXTENSION);
    Path inputDir =
        Paths.get(
            ToolHelper.EXAMPLES_ANDROID_O_BUILD_DIR, "classes", "desugaringwithmissingclasstest1");
    List<Path> input =
        ImmutableList.of(
            inputDir.resolve("ImplementMethodsWithDefault.class"), inputDir.resolve("Main.class"));

    Path mainDexList = temp.getRoot().toPath().resolve("maindexlist.txt");
    FileUtils.writeTextFile(mainDexList, "desugaringwithmissingclasstest1/Main.class");

    Path mainDexRules = temp.getRoot().toPath().resolve("maindex.rules");
    FileUtils.writeTextFile(mainDexRules, "# empty file");

    // It is important to place the api usage sample jar after the current classpath because we want
    // to find D8/R8 classes before the ones in the jar, otherwise renamed classes and fields cannot
    // be found.
    String classPath = System.getProperty("java.class.path") + File.pathSeparator + jar;
    List<String> command =
        ImmutableList.<String>builder()
            .addAll(
                ImmutableList.of(
                    ToolHelper.getJavaExecutable(),
                    "-cp",
                    classPath,
                    main,
                    // Compiler arguments.
                    "--output",
                    temp.newFolder().getAbsolutePath(),
                    "--min-api",
                    Integer.toString(minApiLevel),
                    "--main-dex-list",
                    mainDexList.toString(),
                    "--main-dex-rules",
                    mainDexRules.toString(),
                    "--lib",
                    ToolHelper.getAndroidJar(AndroidApiLevel.getAndroidApiLevel(minApiLevel))
                        .toString(),
                    "--classpath",
                    lib1.toString(),
                    "--classpath",
                    lib2.toString()))
            .addAll(input.stream().map(Path::toString).collect(Collectors.toList()))
            .build();

    ProcessBuilder builder = new ProcessBuilder(command);
    ProcessResult result = ToolHelper.runProcess(builder);
    assertEquals(result.stderr + "\n" + result.stdout, 0, result.exitCode);
    Assert.assertEquals("", filterOutMainDexListWarnings(result.stdout));
    Assert.assertEquals("", result.stderr);
  }

  public static String filterOutMainDexListWarnings(String output) {
    StringBuilder builder = new StringBuilder();
    for (String line : output.split("\n")) {
      if (!line.contains("Unsupported usage of main-dex list")) {
        builder.append(line).append("\n");
      }
    }
    return builder.toString();
  }
}
