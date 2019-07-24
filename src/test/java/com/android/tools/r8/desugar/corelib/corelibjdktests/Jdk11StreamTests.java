// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.corelib.corelibjdktests;

import static com.android.tools.r8.ToolHelper.JDK_TESTS_BUILD_DIR;
import static com.android.tools.r8.utils.FileUtils.CLASS_EXTENSION;
import static com.android.tools.r8.utils.FileUtils.JAVA_EXTENSION;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.D8TestRunResult;
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
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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

  private static String[] RUNNABLE_TESTS =
      new String[] {

        // Working tests
        "org/openjdk/tests/java/util/SplittableRandomTest.java",
        "org/openjdk/tests/java/util/MapTest.java",
        "org/openjdk/tests/java/util/FillableStringTest.java",
        "org/openjdk/tests/java/util/stream/ForEachOpTest.java",
        "org/openjdk/tests/java/util/stream/CollectionAndMapModifyStreamTest.java",
        "org/openjdk/tests/java/util/stream/GroupByOpTest.java",
        "org/openjdk/tests/java/util/stream/InfiniteStreamWithLimitOpTest.java",
        "org/openjdk/tests/java/util/stream/PrimitiveAverageOpTest.java",
        "org/openjdk/tests/java/util/stream/TeeOpTest.java",
        "org/openjdk/tests/java/util/stream/MinMaxTest.java",
        "org/openjdk/tests/java/util/stream/ConcatTest.java",
        // Disabled because time to run > 1 min.
        // "org/openjdk/tests/java/util/stream/CountLargeTest.java",
        "org/openjdk/tests/java/util/stream/StreamParSeqTest.java",
        "org/openjdk/tests/java/util/stream/ReduceByOpTest.java",
        "org/openjdk/tests/java/util/stream/ConcatOpTest.java",
        "org/openjdk/tests/java/util/stream/IntReduceTest.java",
        "org/openjdk/tests/java/util/stream/SortedOpTest.java",
        "org/openjdk/tests/java/util/stream/MatchOpTest.java",
        // Disabled because tim to run > 1 min.
        // "org/openjdk/tests/java/util/stream/RangeTest.java",
        "org/openjdk/tests/java/util/stream/IntSliceOpTest.java",
        "org/openjdk/tests/java/util/stream/SequentialOpTest.java",
        "org/openjdk/tests/java/util/stream/PrimitiveSumTest.java",
        "org/openjdk/tests/java/util/stream/IterateTest.java",
        "org/openjdk/tests/java/util/stream/ReduceTest.java",
        "org/openjdk/tests/java/util/stream/IntUniqOpTest.java",

        // J9 failure
        "org/openjdk/tests/java/util/stream/SpliteratorTest.java",
        // Disabled because tim to run > 1 min.
        // "org/openjdk/tests/java/util/stream/CollectorsTest.java",
        "org/openjdk/tests/java/util/stream/WhileOpStatefulTest.java",
        "org/openjdk/tests/java/util/stream/WhileOpTest.java",

        // J9 security
        "org/openjdk/tests/java/util/stream/CountTest.java",
        // Disabled because time to run > 1 min.
        // "org/openjdk/tests/java/util/stream/FlatMapOpTest.java",
        "org/openjdk/tests/java/util/stream/StreamCloseTest.java",
        "org/openjdk/tests/java/util/stream/DoublePrimitiveOpsTests.java",
        // Disabled because time to run > 1 min.
        // "org/openjdk/tests/java/util/stream/StreamSpliteratorTest.java",
        "org/openjdk/tests/java/util/stream/CollectAndSummaryStatisticsTest.java",

        // Foreach problem
        // Disabled because time to run > 1 min.
        // "org/openjdk/tests/java/util/stream/StreamLinkTest.java",
        "org/openjdk/tests/java/util/stream/FindFirstOpTest.java",
        "org/openjdk/tests/java/util/stream/FindAnyOpTest.java",
        // Disabled because time to run > 1 min.
        // "org/openjdk/tests/java/util/stream/StreamBuilderTest.java",
        // Disabled because time to run > 1 min.
        // "org/openjdk/tests/java/util/stream/SliceOpTest.java",
        "org/openjdk/tests/java/util/stream/DistinctOpTest.java",
        "org/openjdk/tests/java/util/stream/MapOpTest.java",
        // Disabled because time to run > 1 min.
        // "org/openjdk/tests/java/util/stream/ToArrayOpTest.java",

        // J9 Random problem
        "org/openjdk/tests/java/util/stream/LongPrimitiveOpsTests.java",
        "org/openjdk/tests/java/util/stream/IntPrimitiveOpsTests.java"
      };

  private static Map<String, String> getRunnableTests() {
    IdentityHashMap<String, String> pathToName = new IdentityHashMap<>();
    int javaExtSize = JAVA_EXTENSION.length();
    for (String runnableTest : RUNNABLE_TESTS) {
      String nameWithoutJavaExt = runnableTest.substring(0, runnableTest.length() - javaExtSize);
      pathToName.put(
          JDK_11_STREAM_TEST_CLASSES_DIR + "/" + nameWithoutJavaExt + CLASS_EXTENSION,
          nameWithoutJavaExt.replace("/", "."));
    }
    return pathToName;
  }

  private static String[] missingDesugaredMethods() {
    // These methods are from Java 9 and not supported in the current desugared libraries.
    return new String[] {
      // Stream
      "takeWhile(",
      "dropWhile(",
      "iterate(",
      "ofNullable(",
      "range(",
      "doubles(",
      // Collectors
      "filtering(",
      "flatMapping(",
      // List
      "of(",
      // Optional
      "or(",
      "ifPresentOrElse("
    };
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
    // TODO(b/137876068): It seems to fail on windows because the method
    // getAllFilesWithSuffixInDirectory() finds different files on Windows (To be confirmed), so
    // compilation is then different and raises an error.
    Assume.assumeFalse(ToolHelper.isWindows());
    Assume.assumeTrue("TODO(134732760): Fix Android 7+.", requiresCoreLibDesugaring(parameters));
    Map<String, String> runnableTests = getRunnableTests();
    String verbosity = "2";
    List<Path> filesToCompile =
        Arrays.stream(JDK_11_STREAM_TEST_COMPILED_FILES)
            .filter(file -> !file.toString().contains("lang/invoke"))
            .collect(Collectors.toList());
    D8TestCompileResult compileResult =
        testForD8()
            .addProgramFiles(filesToCompile)
            .addProgramFiles(getPathsFiles())
            .addProgramFiles(getSafeVarArgsFile())
            .addProgramFiles(testNGSupportProgramFiles())
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
            .setMinApi(parameters.getApiLevel())
            .enableCoreLibraryDesugaring()
            .compile()
            .addRunClasspathFiles(
                buildDesugaredLibraryWithJavaBaseExtension(parameters.getApiLevel()))
            .withArtFrameworks()
            .withArt6Plus64BitsLib();
    int numSuccesses = 0;
    int numHardFailures = 0;
    for (String path : runnableTests.keySet()) {
      System.out.println(path);
      System.out.println(LocalDateTime.now());
      assert runnableTests.get(path) != null;
      D8TestRunResult result =
          compileResult.run(
              parameters.getRuntime(), "TestNGMainRunner", verbosity, runnableTests.get(path));
      if (result.getStdOut().endsWith(StringUtils.lines("SUCCESS"))) {
        assertTrue(
            result
                .getStdOut()
                .endsWith(
                    StringUtils.lines("Tests result in " + runnableTests.get(path) + ": SUCCESS")));
        numSuccesses++;
      } else {
        if (result.getStdOut().contains("java.lang.NoSuchMethodError")
            && Arrays.stream(missingDesugaredMethods())
                .anyMatch(method -> result.getStdOut().contains(method))) {
          // TODO(b/134732760): support Java 9 APIs.
        } else if (result
            .getStdOut()
            .contains("java.lang.NoSuchMethodError: No interface method forEach")) {
          // TODO(b/134732760): fix tests no to use Iterable#forEach
        } else if (result.getStdOut().contains("in class Ljava/util/Random")
            && result.getStdOut().contains("java.lang.NoSuchMethodError")) {
          // TODO(b/134732760): Random Java 9 Apis, support or do not use them.
        } else if (result.getStdOut().contains("java.lang.AssertionError")) {
          // TODO(b/134732760): Investigate and fix these issues.
          numHardFailures++;
        } else {
          fail();
        }
      }
    }
    assertTrue(numSuccesses > 20);
    assertTrue(numHardFailures < 5);
  }
}
