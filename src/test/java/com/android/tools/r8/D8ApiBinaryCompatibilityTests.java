// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.utils.FileUtils.JAR_EXTENSION;

import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class D8ApiBinaryCompatibilityTests {

  @Rule
  public TemporaryFolder temp = ToolHelper.getTemporaryFolderForTest();

  @Test
  public void testCompatibilityDeprecatedApi() throws IOException {
    Path compilerJar = Paths.get("tests", "api_usage_sample.jar");
    String compiler = "com.android.tools.apiusagesample.D8Compiler";
    String output = temp.newFolder().getAbsolutePath();
    int minSdkVersion = AndroidApiLevel.K.getLevel();
    String androidJar = ToolHelper.getAndroidJar(minSdkVersion);
    Path lib1 = Paths.get(ToolHelper.EXAMPLES_ANDROID_O_BUILD_DIR,
        "desugaringwithmissingclasslib1" + JAR_EXTENSION);
    Path lib2 = Paths.get(ToolHelper.EXAMPLES_ANDROID_O_BUILD_DIR,
        "desugaringwithmissingclasslib2" + JAR_EXTENSION);
    Path input = Paths.get(ToolHelper.EXAMPLES_ANDROID_O_BUILD_DIR,
        "classes", "desugaringwithmissingclasstest1");
    File mainDexClasses = temp.newFile();
    Files.asCharSink(mainDexClasses, StandardCharsets.UTF_8)
        .write("desugaringwithmissingclasstest1/Main.class");

    List<String> command = ImmutableList.of(
        ToolHelper.getJavaExecutable(),
        "-cp",
        compilerJar.toString() + File.pathSeparator + System.getProperty("java.class.path"),
        compiler,
        // Compiler arguments.
        output,
        input.toString(),
        Integer.toString(minSdkVersion),
        mainDexClasses.getAbsolutePath(),
        androidJar,
        lib1.toString(),
        lib2.toString());
    ProcessBuilder builder = new ProcessBuilder(command);
    ProcessResult result = ToolHelper.runProcess(builder);
    Assert.assertEquals(result.stderr + "\n" + result.stdout, 0, result.exitCode);
    Assert.assertTrue(result.stdout, result.stdout.isEmpty());
    Assert.assertTrue(result.stderr, result.stderr.isEmpty());
  }

  @Test
  public void testCompatibilityNewApi() throws IOException {
    Path jar = Paths.get("tests", "d8_api_usage_sample.jar");
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
    File mainDexClasses = temp.newFile();
    Files.asCharSink(mainDexClasses, StandardCharsets.UTF_8)
        .write("desugaringwithmissingclasstest1/Main.class");

    List<String> command =
        ImmutableList.<String>builder()
            .addAll(
                ImmutableList.of(
                    ToolHelper.getJavaExecutable(),
                    "-cp",
                    jar.toString() + File.pathSeparator + System.getProperty("java.class.path"),
                    main,
                    // Compiler arguments.
                    "--output",
                    temp.newFolder().getAbsolutePath(),
                    "--min-api",
                    Integer.toString(minApiLevel),
                    "--lib",
                    ToolHelper.getAndroidJar(minApiLevel),
                    "--classpath",
                    lib1.toString(),
                    "--classpath",
                    lib2.toString()))
            .addAll(input.stream().map(Path::toString).collect(Collectors.toList()))
            .build();

    ProcessBuilder builder = new ProcessBuilder(command);
    ProcessResult result = ToolHelper.runProcess(builder);
    Assert.assertEquals(result.stderr + "\n" + result.stdout, 0, result.exitCode);
    Assert.assertTrue(result.stdout, result.stdout.isEmpty());
    Assert.assertTrue(result.stderr, result.stderr.isEmpty());
  }
}
