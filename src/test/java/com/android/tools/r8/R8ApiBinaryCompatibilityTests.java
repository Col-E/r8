// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class R8ApiBinaryCompatibilityTests {

  static final Path JAR = Paths.get("tests", "r8_api_usage_sample.jar");
  static final String MAIN = "com.android.tools.apiusagesample.R8ApiUsageSample";
  static final int MIN_API = AndroidApiLevel.K.getLevel();

  @Rule public TemporaryFolder temp = ToolHelper.getTemporaryFolderForTest();

  @Test
  public void testCompatibility() throws IOException {
    List<Path> inputs =
        ImmutableList.of(Paths.get(ToolHelper.EXAMPLES_BUILD_DIR, "arithmetic.jar"));

    Path pgConf = temp.getRoot().toPath().resolve("pg.conf");
    FileUtils.writeTextFile(
        pgConf, TestBase.keepMainProguardConfiguration("arithmetic.Arithmetic"));

    Path mainDexRules = temp.getRoot().toPath().resolve("maindex.rules");
    FileUtils.writeTextFile(
        mainDexRules, TestBase.keepMainProguardConfiguration("arithmetic.Arithmetic"));

    Path mainDexList = temp.getRoot().toPath().resolve("maindexlist.txt");
    FileUtils.writeTextFile(mainDexList, "arithmetic/Arithmetic.class");

    List<String> command =
        ImmutableList.<String>builder()
            .addAll(
                ImmutableList.of(
                    ToolHelper.getJavaExecutable(),
                    "-cp",
                    JAR.toString() + File.pathSeparator + System.getProperty("java.class.path"),
                    MAIN,
                    // Compiler arguments.
                    "--output",
                    temp.newFolder().toString(),
                    "--min-api",
                    Integer.toString(MIN_API),
                    "--pg-conf",
                    pgConf.toString(),
                    "--main-dex-rules",
                    mainDexRules.toString(),
                    "--main-dex-list",
                    mainDexList.toString(),
                    "--lib",
                    ToolHelper.getAndroidJar(MIN_API)))
            .addAll(inputs.stream().map(Path::toString).collect(Collectors.toList()))
            .build();

    ProcessBuilder builder = new ProcessBuilder(command);
    ProcessResult result = ToolHelper.runProcess(builder);
    Assert.assertEquals(result.stderr + "\n" + result.stdout, 0, result.exitCode);
    Assert.assertTrue(result.stdout, result.stdout.isEmpty());
    Assert.assertTrue(result.stderr, result.stderr.isEmpty());
  }
}
