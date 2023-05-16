// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.locale;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.D8;
import com.android.tools.r8.R8;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.ZipUtils.ZipBuilder;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TurkishLocaleMultiReleaseJarTest extends TestBase {

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
            ImmutableList.of("-Duser.language=tr"),
            D8.class,
            ImmutableList.of(
                "--min-api",
                Integer.toString(parameters.getApiLevel().getLevel()),
                "--lib",
                ToolHelper.getAndroidJar(AndroidApiLevel.U).toAbsolutePath().toString(),
                buildMultiReleaseJarWithUpperCaseMetaInf(workingDir).toString()));
    checkResult(result);
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
            .toString(),
        buildMultiReleaseJarWithUpperCaseMetaInf(workingDir).toString());
    if (parameters.isCfRuntime()) {
      builder.add("--classfile");
    } else {
      builder.add("--min-api", Integer.toString(parameters.getApiLevel().getLevel()));
    }

    ProcessResult result =
        ToolHelper.forkJavaWithJavaOptions(
            workingDir, ImmutableList.of("-Duser.language=tr"), R8.class, builder.build());
    checkResult(result);
  }

  private Path buildMultiReleaseJarWithUpperCaseMetaInf(Path dir) throws Exception {
    // Compiler will to check String.toLowerCase() of zip entries.
    Path jar = dir.resolve("test.jar");
    ZipBuilder.builder(jar)
        .addFilesRelative(
            ToolHelper.getClassPathForTests(), ToolHelper.getClassFileForTestClass(TestClass.class))
        .addFile(
            Paths.get("META-INF/versions/9")
                .resolve(
                    ToolHelper.getClassPathForTests()
                        .relativize(ToolHelper.getClassFileForTestClass(TestClass.class)))
                .toString(),
            ToolHelper.getClassFileForTestClass(TestClass.class))
        .build();
    return jar;
  }

  private void checkResult(ProcessResult result) {
    assertEquals(1, result.exitCode);
    assertThat(
        result.stderr,
        containsString("Type " + TestClass.class.getTypeName() + " is defined multiple times"));
  }

  static class TestClass {
    public static void main(String[] args) {
      System.out.println("Hello, world!");
    }
  }
}
