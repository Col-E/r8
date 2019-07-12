// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.corelib.corelibjdktests;

import static com.android.tools.r8.ToolHelper.JDK_TESTS_BUILD_DIR;
import static com.android.tools.r8.utils.FileUtils.CLASS_EXTENSION;
import static com.android.tools.r8.utils.FileUtils.JAVA_EXTENSION;
import static org.hamcrest.CoreMatchers.endsWith;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class Jdk11StreamTests extends Jdk11CoreLibTestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    // TODO(134732760): Support Dalvik VMs, currently fails because libjavacrypto is required and
    // present only in ART runtimes.
    return getTestParameters()
        .withDexRuntimesStartingFromIncluding(Version.V5_1_1)
        .withAllApiLevels()
        .build();
  }

  public Jdk11StreamTests(TestParameters parameters) {
    this.parameters = parameters;
  }

  private static final Path JDK_11_STREAM_TEST_CLASSES_DIR =
      Paths.get(ToolHelper.JDK_11_TESTS_CLASSES_DIR + "Stream");
  private static final Path JDK_11_STREAM_TEST_FILES_DIR =
      Paths.get("third_party/openjdk/jdk-11-test/java/util/stream/test");
  private static Path[] JDK_11_STREAM_TEST_COMPILED_FILES;

  private static Path[] getJdk11StreamTestFiles() throws Exception {
    Path[] files = getAllFilesWithSuffixInDirectory(JDK_11_STREAM_TEST_FILES_DIR, JAVA_EXTENSION);
    assert files.length > 0;
    return files;
  }

  @BeforeClass
  public static void compileJdk11StreamTests() throws Exception {
    if (!new File(JDK_11_STREAM_TEST_CLASSES_DIR.toString()).exists()) {
      List<String> options =
          Arrays.asList(
              "--add-reads",
              "java.base=ALL-UNNAMED",
              "--patch-module",
              "java.base=" + JDK_11_JAVA_BASE_EXTENSION_CLASSES_DIR);
      ToolHelper.runJavac(
          CfVm.JDK11,
          Collections.singletonList(Paths.get(JDK_TESTS_BUILD_DIR + "testng-6.10.jar")),
          JDK_11_STREAM_TEST_CLASSES_DIR,
          options,
          getJdk11StreamTestFiles());
    }
    JDK_11_STREAM_TEST_COMPILED_FILES =
        getAllFilesWithSuffixInDirectory(JDK_11_STREAM_TEST_CLASSES_DIR, CLASS_EXTENSION);
    assert JDK_11_STREAM_TEST_COMPILED_FILES.length > 0;
  }

  @Test
  public void testStream() throws Exception {
    Assume.assumeTrue("TODO(134732760): Fix Android 7+.", requiresCoreLibDesugaring(parameters));
    // TODO(134732760): support all tests in JDK_11_STREAM_TEST_COMPILED_FILES.
    Path knownWorkingTest =
        Paths.get(
            JDK_11_STREAM_TEST_CLASSES_DIR
                + "/org/openjdk/tests/java/util/stream/PrimitiveSumTest.class");
    String verbosity = "2";
    testForD8()
        .addProgramFiles(getPathsFiles())
        .addProgramFiles(getSafeVarArgsFile())
        .addProgramFiles(
            Paths.get(ToolHelper.JAVA_CLASSES_DIR + "examplesTestNGRunner/TestNGMainRunner.class"))
        .addProgramFiles(knownWorkingTest)
        .addProgramFiles(Paths.get(JDK_TESTS_BUILD_DIR + "testng-6.10.jar"))
        .addProgramFiles(Paths.get(JDK_TESTS_BUILD_DIR + "jcommander-1.48.jar"))
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .setMinApi(parameters.getRuntime())
        .addOptionsModification(this::configureCoreLibDesugarForProgramCompilation)
        .compile()
        .addRunClasspathFiles(buildDesugaredLibraryWithJavaBaseExtension(parameters.getApiLevel()))
        .withArtFrameworks()
        .withArt6Plus64BitsLib()
        .run(
            parameters.getRuntime(),
            "TestNGMainRunner",
            verbosity,
            "org.openjdk.tests.java.util.stream.PrimitiveSumTest")
        .assertSuccessWithOutputThatMatches(
            endsWith(
                StringUtils.lines(
                    "Tests result in org.openjdk.tests.java.util.stream.PrimitiveSumTest:"
                        + " SUCCESS")));
  }
}
