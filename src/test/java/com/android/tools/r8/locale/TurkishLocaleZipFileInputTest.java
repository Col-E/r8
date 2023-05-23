// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.locale;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.D8;
import com.android.tools.r8.R8;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ArtCommandBuilder;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ZipUtils.ZipBuilder;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TurkishLocaleZipFileInputTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    Path workingDir = temp.getRoot().toPath();
    ProcessResult result =
        ToolHelper.forkJavaWithJavaOptions(
            workingDir,
            // See b/281774632 for context.
            ImmutableList.of("-Duser.language=tr"),
            D8.class,
            ImmutableList.of(
                "--min-api",
                Integer.toString(parameters.getApiLevel().getLevel()),
                "--lib",
                ToolHelper.getAndroidJar(AndroidApiLevel.U).toAbsolutePath().toString(),
                buildZipWithUpperCaseExtension(workingDir).toAbsolutePath().toString()));
    assertEquals(0, result.exitCode);
    runArtOnClassesDotDex(workingDir);
  }

  @Test
  public void testR8() throws Exception {
    Path workingDir = temp.getRoot().toPath();
    ImmutableList.Builder<String> builder = ImmutableList.builder();
    builder.add(
        "--lib",
        ToolHelper.getAndroidJar(AndroidApiLevel.U).toAbsolutePath().toString(),
        "--pg-conf",
        FileUtils.writeTextFile(temp.newFile("test.pro").toPath(), "-keep class * { *; }")
            .toAbsolutePath()
            .toString(),
        buildZipWithUpperCaseExtension(workingDir).toAbsolutePath().toString());
    if (parameters.isCfRuntime()) {
      builder.add("--classfile");
    } else {
      builder.add("--min-api", Integer.toString(parameters.getApiLevel().getLevel()));
    }

    ProcessResult result =
        ToolHelper.forkJavaWithJavaOptions(
            workingDir,
            // See b/281774632 for context.
            ImmutableList.of("-Duser.language=tr"),
            R8.class,
            builder.build());
    assertEquals(0, result.exitCode);
    runArtOnClassesDotDex(workingDir);
  }

  private Path buildZipWithUpperCaseExtension(Path dir) throws Exception {
    // Compiler will to check String.toLowerCase() of file extensions.
    Path jar = dir.resolve("test.ZIP");
    ZipBuilder.builder(jar)
        .addFilesRelative(
            ToolHelper.getClassPathForTests(), ToolHelper.getClassFileForTestClass(TestClass.class))
        .build();
    return jar;
  }

  private void runArtOnClassesDotDex(Path dir) throws Exception {
    if (parameters.getRuntime().isCf()) {
      return;
    }
    ArtCommandBuilder builder = new ArtCommandBuilder(parameters.getRuntime().asDex().getVm());
    builder.appendClasspath(dir.resolve("classes.dex").toAbsolutePath().toString());
    builder.setMainClass(TestClass.class.getTypeName());
    String stdout = ToolHelper.runArt(builder);
    assertEquals(StringUtils.lines("Hello, world!"), stdout);
  }

  static class TestClass {
    public static void main(String[] args) {
      System.out.println("Hello, world!");
    }
  }
}
