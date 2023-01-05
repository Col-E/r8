// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.jdktests;

import static com.android.tools.r8.ToolHelper.JDK_TESTS_BUILD_DIR;
import static com.android.tools.r8.desugar.desugaredlibrary.jdktests.Jdk11SupportFiles.testNGSupportProgramFiles;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8DEBUG;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8SHRINK;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_MINIMAL;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;
import static com.android.tools.r8.utils.FileUtils.CLASS_EXTENSION;
import static com.android.tools.r8.utils.FileUtils.JAVA_EXTENSION;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.DesugaredLibraryTestCompileResult;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class Jdk11ConcurrentLinkedQueueTests extends DesugaredLibraryTestBase {

  private static final String WHITEBOX = "WhiteBox";

  private static Path[] JDK_11_CONCURRENT_LINKED_QUEUE_TEST_CLASS_FILES;

  // JDK 11 test constants.
  private static final Path JDK_11_CONCURRENT_LINKED_QUEUE_JAVA_DIR =
      Paths.get(ToolHelper.JDK_11_TESTS_DIR + "java/util/concurrent/ConcurrentLinkedQueue");
  private static final Path[] JDK_11_CONCURRENT_LINKED_QUEUE_JAVA_FILES =
      new Path[] {JDK_11_CONCURRENT_LINKED_QUEUE_JAVA_DIR.resolve(WHITEBOX + JAVA_EXTENSION)};

  @Parameter(0)
  public static TestParameters parameters;

  @Parameter(1)
  public static LibraryDesugaringSpecification libraryDesugaringSpecification;

  @Parameter(2)
  public static CompilationSpecification compilationSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        // TODO(134732760): Support Dalvik VMs, currently fails because libjavacrypto is required
        // and present only in ART runtimes.
        getTestParameters()
            .withDexRuntimesStartingFromIncluding(Version.V5_1_1)
            .withAllApiLevels()
            .withApiLevel(AndroidApiLevel.N)
            .build(),
        ImmutableList.of(JDK11_MINIMAL, JDK11, JDK11_PATH),
        ImmutableList.of(D8_L8DEBUG, D8_L8SHRINK));
  }

  @BeforeClass
  public static void compileConcurrentLinkedQueueClasses() throws Exception {
    // Build test constants.
    Path jdk11MathTestsDir = getStaticTemp().newFolder("ConcurrentLinkedQueue").toPath();
    javac(TestRuntime.getCheckedInJdk11(), getStaticTemp())
        .addClasspathFiles(
            Collections.singletonList(Paths.get(JDK_TESTS_BUILD_DIR + "testng-6.10.jar")))
        .addSourceFiles(JDK_11_CONCURRENT_LINKED_QUEUE_JAVA_FILES)
        .setOutputPath(jdk11MathTestsDir)
        .compile();
    JDK_11_CONCURRENT_LINKED_QUEUE_TEST_CLASS_FILES =
        new Path[] {jdk11MathTestsDir.resolve(WHITEBOX + CLASS_EXTENSION)};
  }

  private static void ranWithSuccessOrFailures(String testName, SingleTestRunResult result) {
    // Tests use ThreadLocalRandom, so success or failure is random. Note this is only for
    // VMs where the internal implementation is not based on JDK11.
    assertTrue(
        result.getStdOut().contains(StringUtils.lines(testName + ": SUCCESS"))
            || result
                .getStdOut()
                .contains(StringUtils.lines("Tests result in " + testName + ": FAILURE")));
    if (result.getStdOut().contains(StringUtils.lines(testName + ": SUCCESS"))) {
      assertTrue(
          result.toString(),
          result.getStdOut().contains("Total tests run: 37, Failures: 0, Skips: 0"));
    } else {
      assertTrue(
          result.toString(),
          result.getStdOut().contains("Total tests run: 37, Failures: 1, Skips: 0")
              || result.getStdOut().contains("Total tests run: 37, Failures: 2, Skips: 0")
              || result.getStdOut().contains("Total tests run: 37, Failures: 3, Skips: 0")
              || result.getStdOut().contains("Total tests run: 37, Failures: 4, Skips: 0")
              || result.getStdOut().contains("Total tests run: 37, Failures: 5, Skips: 0")
              || result.getStdOut().contains("Total tests run: 37, Failures: 6, Skips: 0")
              || result.getStdOut().contains("Total tests run: 37, Failures: 7, Skips: 0")
              || result.getStdOut().contains("Total tests run: 37, Failures: 8, Skips: 0"));
    }
  }

  void runTest(List<String> toRun) throws Exception {
    String verbosity = "2";
    DesugaredLibraryTestCompileResult<?> compileResult =
        testForDesugaredLibrary(
                parameters, libraryDesugaringSpecification, compilationSpecification)
            .addProgramFiles(JDK_11_CONCURRENT_LINKED_QUEUE_TEST_CLASS_FILES)
            .addProgramFiles(testNGSupportProgramFiles())
            // The WhiteBox test is using VarHandle and MethodHandles.privateLookupIn to inspect the
            // internal state of the implementation, so desugaring is needed for the program here.
            .addOptionsModification(options -> options.enableVarHandleDesugaring = true)
            .compile()
            .withArt6Plus64BitsLib();
    for (String success : toRun) {
      SingleTestRunResult<?> result =
          compileResult.run(parameters.getRuntime(), "TestNGMainRunner", verbosity, success);
      if ((parameters.asDexRuntime().getVersion().equals(Version.V5_1_1)
              || parameters.asDexRuntime().getVersion().equals(Version.V6_0_1))
          && libraryDesugaringSpecification == JDK11_MINIMAL) {
        // Some tests use streams, so which is not desugared with JDK11_MINIMAL. These tests are
        // somehow skipped by the test runner used in the JDK11 tests.
        assertTrue(result.getStdOut().contains("Total tests run: 9, Failures: 0, Skips: 7"));
        assertTrue(result.getStdOut().contains(StringUtils.lines(success + ": SUCCESS")));
      } else if (parameters.asDexRuntime().getVersion().isOlderThanOrEqual(Version.V12_0_0)) {
        ranWithSuccessOrFailures(success, result);
      } else {
        assertTrue(parameters.asDexRuntime().getVersion().isNewerThanOrEqual(Version.V13_0_0));
        if (parameters.getApiLevel() == AndroidApiLevel.B) {
          ranWithSuccessOrFailures(success, result);
        } else {
          // No desugaring and JDK11 based runtime implementation.
          assertTrue(
              "Failure in " + success + "\n" + result,
              result.getStdOut().contains(StringUtils.lines(success + ": SUCCESS")));
        }
      }
    }
  }

  @Test
  public void testWhiteBox() throws Exception {
    runTest(ImmutableList.of("WhiteBox"));
  }
}
