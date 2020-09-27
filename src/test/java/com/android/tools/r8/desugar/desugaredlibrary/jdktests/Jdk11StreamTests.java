// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.jdktests;

import static com.android.tools.r8.ToolHelper.JDK_TESTS_BUILD_DIR;
import static com.android.tools.r8.utils.FileUtils.CLASS_EXTENSION;
import static com.android.tools.r8.utils.FileUtils.JAVA_EXTENSION;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.D8TestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class Jdk11StreamTests extends Jdk11DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final boolean shrinkDesugaredLibrary;

  @Parameters(name = "{1}, shrinkDesugaredLibrary: {0}")
  public static List<Object[]> data() {
    // TODO(134732760): Support Dalvik VMs, currently fails because libjavacrypto is required and
    // present only in ART runtimes.
    return buildParameters(
        BooleanUtils.values(),
        getTestParameters()
            .withDexRuntimesStartingFromIncluding(Version.V5_1_1)
            .withAllApiLevels()
            .build());
  }

  public Jdk11StreamTests(boolean shrinkDesugaredLibrary, TestParameters parameters) {
    this.shrinkDesugaredLibrary = shrinkDesugaredLibrary;
    this.parameters = parameters;
  }

  private static Path JDK_11_STREAM_TEST_CLASSES_DIR;
  private static final Path JDK_11_STREAM_TEST_FILES_DIR =
      Paths.get("third_party/openjdk/jdk-11-test/java/util/stream/test");
  private static Path[] JDK_11_STREAM_TEST_COMPILED_FILES;

  private static Path[] getJdk11StreamTestFiles() throws Exception {
    Path[] files = getAllFilesWithSuffixInDirectory(JDK_11_STREAM_TEST_FILES_DIR, JAVA_EXTENSION);
    assert files.length > 0;
    return files;
  }

  private static String[] FAILING_RUNNABLE_TESTS =
      new String[] {
        // J9 failure.
        "org/openjdk/tests/java/util/stream/SpliteratorTest.java",
        "org/openjdk/tests/java/util/stream/WhileOpStatefulTest.java",
        "org/openjdk/tests/java/util/stream/IterateTest.java",
        "org/openjdk/tests/java/util/stream/WhileOpTest.java",
        // Assertion error
        "org/openjdk/tests/java/util/stream/StreamCloseTest.java",
        "org/openjdk/tests/java/util/stream/CollectAndSummaryStatisticsTest.java",
        "org/openjdk/tests/java/util/stream/CountTest.java",
        // J9 Random problem
        "org/openjdk/tests/java/util/stream/LongPrimitiveOpsTests.java",
        "org/openjdk/tests/java/util/stream/IntPrimitiveOpsTests.java",
        "org/openjdk/tests/java/util/stream/DoublePrimitiveOpsTests.java"
        // Disabled because explicit cast done on a wrapped value.
        // "org/openjdk/tests/java/util/SplittableRandomTest.java",
      };

  // Disabled because time to run > 1 min for each test.
  // Can be used for experimentation/testing purposes.
  private static String[] LONG_RUNNING_TESTS =
      new String[] {
        "org/openjdk/tests/java/util/stream/InfiniteStreamWithLimitOpTest.java",
        "org/openjdk/tests/java/util/stream/CountLargeTest.java",
        "org/openjdk/tests/java/util/stream/RangeTest.java",
        "org/openjdk/tests/java/util/stream/CollectorsTest.java",
        "org/openjdk/tests/java/util/stream/FlatMapOpTest.java",
        "org/openjdk/tests/java/util/stream/StreamSpliteratorTest.java",
        "org/openjdk/tests/java/util/stream/StreamLinkTest.java",
        "org/openjdk/tests/java/util/stream/StreamBuilderTest.java",
        "org/openjdk/tests/java/util/stream/SliceOpTest.java",
        "org/openjdk/tests/java/util/stream/ToArrayOpTest.java"
      };

  private static String[] SUCCESSFUL_RUNNABLE_TESTS =
      new String[] {
        "org/openjdk/tests/java/util/stream/FindFirstOpTest.java",
        "org/openjdk/tests/java/util/stream/MapOpTest.java",
        "org/openjdk/tests/java/util/stream/DistinctOpTest.java",
        "org/openjdk/tests/java/util/MapTest.java",
        "org/openjdk/tests/java/util/FillableStringTest.java",
        "org/openjdk/tests/java/util/stream/ForEachOpTest.java",
        "org/openjdk/tests/java/util/stream/CollectionAndMapModifyStreamTest.java",
        "org/openjdk/tests/java/util/stream/GroupByOpTest.java",
        "org/openjdk/tests/java/util/stream/PrimitiveAverageOpTest.java",
        "org/openjdk/tests/java/util/stream/TeeOpTest.java",
        "org/openjdk/tests/java/util/stream/MinMaxTest.java",
        "org/openjdk/tests/java/util/stream/ConcatTest.java",
        "org/openjdk/tests/java/util/stream/StreamParSeqTest.java",
        "org/openjdk/tests/java/util/stream/ReduceByOpTest.java",
        "org/openjdk/tests/java/util/stream/ConcatOpTest.java",
        "org/openjdk/tests/java/util/stream/IntReduceTest.java",
        "org/openjdk/tests/java/util/stream/SortedOpTest.java",
        "org/openjdk/tests/java/util/stream/MatchOpTest.java",
        "org/openjdk/tests/java/util/stream/IntSliceOpTest.java",
        "org/openjdk/tests/java/util/stream/SequentialOpTest.java",
        "org/openjdk/tests/java/util/stream/PrimitiveSumTest.java",
        "org/openjdk/tests/java/util/stream/ReduceTest.java",
        "org/openjdk/tests/java/util/stream/IntUniqOpTest.java",
        "org/openjdk/tests/java/util/stream/FindAnyOpTest.java"
      };

  private static Map<String, String> getRunnableTests(String[] tests) {
    IdentityHashMap<String, String> pathToName = new IdentityHashMap<>();
    int javaExtSize = JAVA_EXTENSION.length();
    for (String runnableTest : tests) {
      String nameWithoutJavaExt = runnableTest.substring(0, runnableTest.length() - javaExtSize);
      pathToName.put(
          JDK_11_STREAM_TEST_CLASSES_DIR + "/" + nameWithoutJavaExt + CLASS_EXTENSION,
          nameWithoutJavaExt.replace("/", "."));
    }
    return pathToName;
  }

  private static String[] missingDesugaredMethods() {
    // These methods are from Java 9 and not supported in the current desugared libraries.
    return new String[]{
        // Stream
        "takeWhile(",
        "dropWhile(",
        "iterate(",
        "range(",
        "doubles(",
        // Collectors
        "filtering(",
        "flatMapping(",
    };
  }

  @BeforeClass
  public static void compileJdk11StreamTests() throws Exception {
    JDK_11_STREAM_TEST_CLASSES_DIR = getStaticTemp().newFolder("stream").toPath();
    List<String> options =
        Arrays.asList(
            "--add-reads",
            "java.base=ALL-UNNAMED",
            "--patch-module",
            "java.base=" + JDK_11_JAVA_BASE_EXTENSION_CLASSES_DIR);
    javac(TestRuntime.getCheckedInJdk11(), getStaticTemp())
        .addOptions(options)
        .addClasspathFiles(
            Collections.singletonList(Paths.get(JDK_TESTS_BUILD_DIR + "testng-6.10.jar")))
        .addSourceFiles(getJdk11StreamTestFiles())
        .setOutputPath(JDK_11_STREAM_TEST_CLASSES_DIR)
        .compile();
    JDK_11_STREAM_TEST_COMPILED_FILES =
        getAllFilesWithSuffixInDirectory(JDK_11_STREAM_TEST_CLASSES_DIR, CLASS_EXTENSION);
    assert JDK_11_STREAM_TEST_COMPILED_FILES.length > 0;
  }

  @Test
  public void testStream() throws Exception {
    expectThrowsWithHorizontalClassMergingIf(
        shrinkDesugaredLibrary && parameters.getApiLevel().isLessThan(AndroidApiLevel.N));
    Assume.assumeFalse(
        "getAllFilesWithSuffixInDirectory() seems to find different files on Windows",
        ToolHelper.isWindows());
    Assume.assumeTrue(
        "Requires Java base extensions, should add it when not desugaring",
        parameters.getApiLevel().getLevel() < AndroidApiLevel.N.getLevel());

    D8TestCompileResult compileResult = compileStreamTestsToDex();
    runSuccessfulTests(compileResult);
    runFailingTests(compileResult);
  }

  private D8TestCompileResult compileStreamTestsToDex() throws Exception {
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    List<Path> filesToCompile =
        Arrays.stream(JDK_11_STREAM_TEST_COMPILED_FILES)
            .filter(file -> !file.toString().contains("lang/invoke"))
            .collect(Collectors.toList());
    return testForD8()
        .addProgramFiles(filesToCompile)
        .addProgramFiles(getPathsFiles())
        .addProgramFiles(getSafeVarArgsFile())
        .addProgramFiles(testNGSupportProgramFiles())
        .addOptionsModification(opt -> opt.testing.trackDesugaredAPIConversions = true)
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibraryWithJavaBaseExtension,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .withArtFrameworks()
        .withArt6Plus64BitsLib();
  }

  private void runSuccessfulTests(D8TestCompileResult compileResult) throws Exception {
    String verbosity = "2"; // Increase verbosity for debugging.
    Map<String, String> runnableTests = getRunnableTests(SUCCESSFUL_RUNNABLE_TESTS);
    for (String path : runnableTests.keySet()) {
      assert runnableTests.get(path) != null;
      D8TestRunResult result =
          compileResult.run(
              parameters.getRuntime(), "TestNGMainRunner", verbosity, runnableTests.get(path));
      assertTrue(
          "Failure in " + path + "\n" + result,
          result
              .getStdOut()
              .endsWith(
                  StringUtils.lines("Tests result in " + runnableTests.get(path) + ": SUCCESS")));
    }
  }

  private void runFailingTests(D8TestCompileResult compileResult) throws Exception {
    // For failing runnable tests, we just ensure that they do not fail due to desugaring, but
    // due to an expected failure (missing API, etc.).
    String verbosity = "2"; // Increase verbosity for debugging.
    Map<String, String> runnableTests = getRunnableTests(FAILING_RUNNABLE_TESTS);
    for (String path : runnableTests.keySet()) {
      assert runnableTests.get(path) != null;
      D8TestRunResult result =
          compileResult.run(
              parameters.getRuntime(), "TestNGMainRunner", verbosity, runnableTests.get(path));
      if (result.getStdOut().contains("java.lang.NoSuchMethodError")
          && Arrays.stream(missingDesugaredMethods())
              .anyMatch(method -> result.getStdOut().contains(method))) {
        // TODO(b/134732760): support Java 9 APIs.
      } else if (result.getStdOut().contains("in class Ljava/util/Random")
          && result.getStdOut().contains("java.lang.NoSuchMethodError")) {
        // TODO(b/134732760): Random Java 9 Apis, support or do not use them.
      } else if (result.getStdOut().contains("java.lang.AssertionError")) {
        // TODO(b/134732760): Investigate and fix these issues.
      } else {
        String errorMessage = "STDOUT:\n" + result.getStdOut() + "STDERR:\n" + result.getStdErr();
        fail(errorMessage);
      }
    }
  }
}
