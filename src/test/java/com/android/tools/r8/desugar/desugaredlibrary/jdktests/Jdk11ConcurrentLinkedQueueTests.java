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

  void runTest(List<String> toRun) throws Exception {
    String verbosity = "2";
    DesugaredLibraryTestCompileResult<?> compileResult =
        testForDesugaredLibrary(
                parameters, libraryDesugaringSpecification, compilationSpecification)
            .addProgramFiles(JDK_11_CONCURRENT_LINKED_QUEUE_TEST_CLASS_FILES)
            .addProgramFiles(testNGSupportProgramFiles())
            .compile()
            .withArt6Plus64BitsLib();
    for (String success : toRun) {
      SingleTestRunResult<?> result =
          compileResult.run(parameters.getRuntime(), "TestNGMainRunner", verbosity, success);
      // The WhiteBox test is using VarHandle and MethodHandles.privateLookupIn to inspect the
      // internal state of the implementation so running it fails for various reasons on all VMs
      // before T.
      if (parameters.asDexRuntime().getVersion().equals(Version.V5_1_1)
          || parameters.asDexRuntime().getVersion().equals(Version.V6_0_1)) {
        assertTrue(
            result
                .getStdErr()
                .contains(
                    "java.lang.ClassNotFoundException: Didn't find class"
                        + " \"java.lang.invoke.MethodHandles\""));
      } else if (parameters.asDexRuntime().getVersion().equals(Version.V7_0_0)) {
        assertTrue(
            result
                .getStdErr()
                .contains(
                    "java.lang.NoClassDefFoundError: Failed resolution of:"
                        + " Ljava/lang/invoke/MethodHandles;"));
      } else if (parameters.asDexRuntime().getVersion().isOlderThanOrEqual(Version.V12_0_0)) {
        if (parameters.getApiLevel() == AndroidApiLevel.O
            || parameters.getApiLevel() == AndroidApiLevel.O_MR1) {
          assertTrue(result.getStdErr().contains("Verification error"));
        } else {
          assertTrue(
              result
                  .getStdErr()
                  .contains(
                      "java.lang.NoSuchMethodError: No static method"
                          + " privateLookupIn(Ljava/lang/Class;Ljava/lang/invoke/MethodHandles$Lookup;)Ljava/lang/invoke/MethodHandles$Lookup;"
                          + " in class Ljava/lang/invoke/MethodHandles;"));
        }
      } else {
        assertTrue(parameters.asDexRuntime().getVersion().isNewerThanOrEqual(Version.V13_0_0));
        if (parameters.getApiLevel() == AndroidApiLevel.B
            || parameters.getApiLevel() == AndroidApiLevel.N) {
          assertTrue(result.getStdOut().contains("Instruction is unrepresentable in DEX"));
        } else {
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
