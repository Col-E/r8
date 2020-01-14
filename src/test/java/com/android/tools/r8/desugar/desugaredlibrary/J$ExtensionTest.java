// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class J$ExtensionTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final boolean shrinkDesugaredLibrary;

  private static final String MAIN_CLASS_NAME = "Main";
  private static final String MAIN_CLASS =
      "import java.time.LocalTimeAccess;\n"
          + "public class Main {\n"
          + "   public static void main(String[] args) throws Exception { \n"
          + "     LocalTimeAccess.main(new String[0]);\n"
          + "   }\n"
          + "}";

  private static final String TIME_CLASS =
      "package java.time;\n"
          + "\n"
          + "import java.time.LocalTime;\n"
          + "\n"
          + "public class LocalTimeAccess {\n"
          + "\n"
          + "  public static void main(String[] args) throws Exception {\n"
          + "    System.out.println(LocalTime.readExternal(null));\n"
          + "  }\n"
          + "}";
  private static Path[] compiledClasses = new Path[2];

  @Parameters(name = "{1}, shrinkDesugaredLibrary: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withAllRuntimes().withAllApiLevels().build());
  }

  public J$ExtensionTest(boolean shrinkDesugaredLibrary, TestParameters parameters) {
    this.shrinkDesugaredLibrary = shrinkDesugaredLibrary;
    this.parameters = parameters;
  }

  @BeforeClass
  public static void compileClass() throws IOException {
    Assume.assumeFalse(ToolHelper.isWindows());

    File file1 = getStaticTemp().newFile("LocalTimeAccess.java");
    BufferedWriter writer1 = new BufferedWriter(new FileWriter(file1));
    writer1.write(TIME_CLASS);
    writer1.close();
    compiledClasses[0] =
        javac(TestRuntime.getCheckedInJdk8(), getStaticTemp())
            .addSourceFiles(file1.toPath())
            .compile();

    File file2 = getStaticTemp().newFile("Main.java");
    BufferedWriter writer2 = new BufferedWriter(new FileWriter(file2));
    writer2.write(MAIN_CLASS);
    writer2.close();
    compiledClasses[1] =
        javac(TestRuntime.getCheckedInJdk8(), getStaticTemp())
            .addSourceFiles(file2.toPath())
            .addClasspathFiles(compiledClasses[0])
            .compile();
  }

  @Test
  public void testJ$ExtensionNoDesugaring() throws Exception {
    Assume.assumeFalse(shrinkDesugaredLibrary);
    String stderr;
    if (parameters.isCfRuntime()) {
      stderr =
          testForJvm()
              .addProgramFiles(compiledClasses)
              .run(parameters.getRuntime(), MAIN_CLASS_NAME)
              .assertFailure()
              .getStdErr();
    } else {
      stderr =
          testForD8()
              .addProgramFiles(compiledClasses)
              .setMinApi(parameters.getApiLevel())
              .run(parameters.getRuntime(), MAIN_CLASS_NAME)
              .assertFailure()
              .getStdErr();
    }
    assertError(stderr);
  }

  private void assertError(String stderr) {
    if (parameters.isCfRuntime() && parameters.getRuntime().asCf().getVm() == CfVm.JDK8) {
      assertTrue(
          stderr.contains("java.lang.SecurityException: Prohibited package name: java.time"));
    } else if (parameters.isCfRuntime()) {
      assertTrue(stderr.contains("java.lang.ClassNotFoundException: java.time.LocalTimeAccess"));
    } else if (parameters
        .getRuntime()
        .asDex()
        .getVm()
        .getVersion()
        .isOlderThanOrEqual(Version.V6_0_1)) {
      assertTrue(stderr.contains("java.lang.NoClassDefFoundError"));
    } else if (parameters.getRuntime().asDex().getVm().getVersion() == Version.V7_0_0) {
      assertTrue(stderr.contains("java.lang.ClassNotFoundException"));
    } else {
      assertTrue(stderr.contains("java.lang.IllegalAccessError"));
    }
  }

  @Test
  public void testJ$ExtensionDesugaring() throws Exception {
    Assume.assumeFalse(parameters.isCfRuntime());
    // Above O no desugaring is required.
    Assume.assumeTrue(parameters.getApiLevel().getLevel() < AndroidApiLevel.O.getLevel());
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);

    try {
      testForD8()
          .addProgramFiles(compiledClasses)
          .setMinApi(parameters.getApiLevel())
          .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
          .compile();
      fail();
    } catch (CompilationFailedException e) {
      assertTrue(
          e.getCause()
              .getMessage()
              .contains("Cannot compile program class java.time.LocalTimeAccess"));
    }
  }
}
