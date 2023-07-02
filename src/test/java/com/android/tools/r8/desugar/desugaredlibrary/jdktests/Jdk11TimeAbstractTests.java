// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.jdktests;

import static com.android.tools.r8.desugar.desugaredlibrary.jdktests.Jdk11SupportFiles.getPathsFiles;
import static com.android.tools.r8.desugar.desugaredlibrary.jdktests.Jdk11SupportFiles.getTestNGMainRunner;
import static com.android.tools.r8.desugar.desugaredlibrary.jdktests.Jdk11SupportFiles.jcommanderPath;
import static com.android.tools.r8.desugar.desugaredlibrary.jdktests.Jdk11SupportFiles.testNGPath;
import static com.android.tools.r8.desugar.desugaredlibrary.jdktests.Jdk11SupportFiles.testNGSupportProgramFiles;
import static com.android.tools.r8.desugar.desugaredlibrary.jdktests.Jdk11TestLibraryDesugaringSpecification.EXTENSION_PATH;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8DEBUG;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8SHRINK;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK8;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public abstract class Jdk11TimeAbstractTests extends DesugaredLibraryTestBase {

  private static final int SPLIT = 2;
  private static final Path JDK_11_TCK_TEST_FILES_DIR =
      Paths.get(ToolHelper.JDK_11_TIME_TESTS_DIR).resolve("tck");
  private static final Path JDK_11_TIME_TEST_FILES_DIR =
      Paths.get(ToolHelper.JDK_11_TIME_TESTS_DIR).resolve("test");
  private static final String JDK_11_TIME_TEST_EXCLUDE = "TestZoneTextPrinterParser.java";
  private static Path[] JDK_11_TIME_TEST_COMPILED_FILES;

  final TestParameters parameters;
  final LibraryDesugaringSpecification libraryDesugaringSpecification;
  final CompilationSpecification compilationSpecification;

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
        ImmutableList.of(JDK8, JDK11_PATH),
        ImmutableList.of(D8_L8DEBUG, D8_L8SHRINK));
  }

  public Jdk11TimeAbstractTests(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  private static List<Path> getJdk11TimeTestFiles() throws Exception {
    List<Path> tckFiles =
        Files.walk(JDK_11_TCK_TEST_FILES_DIR)
            .filter(path -> path.toString().endsWith(JAVA_EXTENSION))
            .filter(path -> !path.toString().endsWith(JDK_11_TIME_TEST_EXCLUDE))
            .collect(Collectors.toList());
    List<Path> timeFiles =
        Files.walk(JDK_11_TIME_TEST_FILES_DIR)
            .filter(path -> path.toString().endsWith(JAVA_EXTENSION))
            .filter(path -> !path.toString().endsWith(JDK_11_TIME_TEST_EXCLUDE))
            .collect(Collectors.toList());
    ArrayList<Path> files = new ArrayList<>();
    files.addAll(timeFiles);
    files.addAll(tckFiles);
    assert files.size() > 0;
    return files;
  }

  @BeforeClass
  public static void compileJdk11TimeTests() throws Exception {
    Path tmpDirectory = getStaticTemp().newFolder("time").toPath();
    List<String> options =
        Arrays.asList(
            "--add-reads",
            "java.base=ALL-UNNAMED",
            "--patch-module",
            "java.base=" + EXTENSION_PATH);
    javac(TestRuntime.getCheckedInJdk11(), getStaticTemp())
        .addOptions(options)
        .addClasspathFiles(testNGPath(), jcommanderPath())
        .addSourceFiles(getJdk11TimeTestFiles())
        .setOutputPath(tmpDirectory)
        .compile();
    JDK_11_TIME_TEST_COMPILED_FILES =
        getAllFilesWithSuffixInDirectory(tmpDirectory, CLASS_EXTENSION);
    assert JDK_11_TIME_TEST_COMPILED_FILES.length > 0;
  }

  // Following tests are also failing on the Bazel build, they cannot be run easily on
  // Android (difference in time precision, iAndroid printing, etc.).
  private static final String[] WONT_FIX_FAILURES =
      new String[] {
        "test.java.time.TestZoneTextPrinterParser.java",
        // Removed by gradle (compile-time error).
        "tck.java.time.TCKZoneId.java",
        "tck.java.time.TCKZoneOffset.java",
        "tck.java.time.TCKChronology.java",
        "tck.java.time.chrono.TCKTestServiceLoader.java",
        "tck.java.time.TCKCopticSerialization.java",
        "tck.java.time.TCKFormatStyle.java",
        "tck.java.time.TCKZoneRules.java",
        "test.java.time.chrono.TestServiceLoader.java",
        "test.java.time.TestJapaneseChronoImpl.java",
        "test.java.time.TestThaiBuddhistChronoImpl.java",
        "test.java.time.TestDateTimeFormatterBuilder.java",
        "test.java.time.TestDateTimeTextProvider.java",
        "test.java.time.TestNonIsoFormatter.java",
        "test.java.time.TestTextParser.java",
        "test.java.time.TestTextPrinter.java",
        "test.java.time.TestChronoField.java",
        "test.java.util.TestFormatter.java",
        // Following also fails using the default libs on P...
        "test.java.time.chrono.TestEraDisplayName",
        "test.java.time.format.TestDateTimeFormatter",
        "test.java.time.TestLocalDate",
        // Formatting problem
        "test.java.time.format.TestNarrowMonthNamesAndDayNames",
      };
  static final String[] RAW_TEMPORAL_SUCCESSES =
      new String[] {
        "test.java.time.TestYearMonth",
        "test.java.time.TestZonedDateTime",
        "test.java.time.TestClock_Tick",
        "test.java.time.TestMonthDay",
        "test.java.time.zone.TestFixedZoneRules",
        "test.java.time.TestOffsetDateTime",
        "test.java.time.TestInstant",
        "test.java.time.TestDuration",
        "test.java.time.TestZoneOffset",
        "test.java.time.TestLocalDateTime",
        "test.java.time.TestClock_Fixed",
        "test.java.time.TestYear",
        "test.java.time.TestLocalTime",
        "test.java.time.TestZoneId",
        "test.java.time.TestOffsetTime",
        "test.java.time.TestClock_Offset",
        "test.java.time.TestPeriod",
        "test.java.time.TestOffsetDateTime_instants",
        "test.java.time.temporal.TestDateTimeBuilderCombinations",
        "test.java.time.temporal.TestJulianFields",
        "test.java.time.temporal.TestChronoUnit",
        "test.java.time.temporal.TestDateTimeValueRange"
      };
  static final String[] RAW_TEMPORAL_SUCCESSES_IF_BRIDGE =
      new String[] {"tck.java.time.TestIsoChronology"};
  static final String[] RAW_TEMPORAL_SUCCESSES_UP_TO_11 =
      new String[] {"test.java.time.temporal.TestIsoWeekFields"};
  static final String[] RAW_TEMPORAL_SUCCESSES_UP_TO_14 =
      new String[] {
        // Reflective lookup Class.forName("java.time.Clock$SystemClock").getDeclaredField("offset")
        // fails.
        "test.java.time.TestClock_System"
      };
  static final String[] FORMAT_CHRONO_SUCCESSES =
      new String[] {
        "test.java.time.format.TestFractionPrinterParser",
        "test.java.time.format.TestStringLiteralParser",
        "test.java.time.format.TestZoneOffsetPrinter",
        "test.java.time.format.TestDecimalStyle",
        "test.java.time.format.TestCharLiteralPrinter",
        "test.java.time.format.TestStringLiteralPrinter",
        "test.java.time.format.TestPadPrinterDecorator",
        "test.java.time.format.TestNumberPrinter",
        "test.java.time.format.TestZoneOffsetParser",
        "test.java.time.format.TestReducedParser",
        "test.java.time.format.TestDateTimeParsing",
        "test.java.time.format.TestSettingsParser",
        "test.java.time.format.TestNumberParser",
        "test.java.time.format.TestReducedPrinter",
        "test.java.time.format.TestCharLiteralParser",
        "test.java.time.chrono.TestChronologyPerf",
        "test.java.time.chrono.TestExampleCode",
        "test.java.time.chrono.TestJapaneseChronology",
        "test.java.time.chrono.TestChronoLocalDate",
        "test.java.time.chrono.TestIsoChronoImpl",
      };
  static final String[] FORMAT_CHRONO_SUCCESSES_UP_TO_11 =
      new String[] {
        "test.java.time.format.TestDateTimeTextProviderWithLocale",
        "test.java.time.format.TestUnicodeExtension",
        "test.java.time.format.TestDateTimeFormatterBuilderWithLocale",
        "test.java.time.format.TestTextParserWithLocale",
        "test.java.time.format.TestTextPrinterWithLocale",
        "test.java.time.chrono.TestUmmAlQuraChronology",
      };

  public String[] getFormatChronoSuccesses() {
    List<String> allTests = new ArrayList<>();
    Collections.addAll(allTests, FORMAT_CHRONO_SUCCESSES);
    if (parameters.getDexRuntimeVersion().isOlderThan(Version.V12_0_0)) {
      // Formatting issues starting from 12.
      Collections.addAll(allTests, FORMAT_CHRONO_SUCCESSES_UP_TO_11);
    }
    return allTests.toArray(new String[0]);
  }

  public String[] getRawTemporalSuccesses() {
    List<String> allTests = new ArrayList<>();
    Collections.addAll(allTests, RAW_TEMPORAL_SUCCESSES);
    if (parameters.getDexRuntimeVersion().isOlderThan(Version.V12_0_0)) {
      // In 12 some ISO is supported that other versions do not support.
      Collections.addAll(allTests, RAW_TEMPORAL_SUCCESSES_UP_TO_11);
    }
    if (parameters.getDexRuntimeVersion().isOlderThan(Version.V14_0_0)) {
      // In 14 some reflection used in test fails.
      Collections.addAll(allTests, RAW_TEMPORAL_SUCCESSES_UP_TO_14);
    }
    // The bridge is always present with JDK11 due to partial desugaring between 26 and 33.
    // On JDK8 the bridge is absent in between 26 and 33.
    if (libraryDesugaringSpecification != JDK8
        || !parameters.getApiLevel().betweenBothIncluded(AndroidApiLevel.O, AndroidApiLevel.Sv2)) {
      Collections.addAll(allTests, RAW_TEMPORAL_SUCCESSES_IF_BRIDGE);
    }
    return allTests.toArray(new String[0]);
  }

  String[] split(String[] input, int index) {
    return Jdk11TestInputSplitter.split(input, index, SPLIT);
  }

  void compileAndTestTime(String[] toRun) throws Exception {
    // The compilation time is significantly higher than the test time, it is important to compile
    // once and test multiple times on the same artifact for test performance.
    String verbosity = "2";
    DesugaredLibraryTestCompileResult<?> compileResult =
        testForDesugaredLibrary(
                parameters, libraryDesugaringSpecification, compilationSpecification)
            .addProgramFiles(JDK_11_TIME_TEST_COMPILED_FILES)
            .addProgramFiles(testNGSupportProgramFiles())
            .addProgramClassFileData(getTestNGMainRunner())
            .applyIf(
                !libraryDesugaringSpecification.hasNioFileDesugaring(parameters),
                b -> b.addProgramFiles(getPathsFiles()))
            .compile()
            .withArt6Plus64BitsLib();
    for (String success : toRun) {
      SingleTestRunResult<?> result =
          compileResult.run(parameters.getRuntime(), "TestNGMainRunner", verbosity, success);
      if (result.getStdErr().contains("Couldn't find any tzdata")) {
        // TODO(b/134732760): fix missing time zone data.
      } else if (result.getStdErr().contains("no microsecond precision")) {
        // Emulator precision, won't fix.
      } else {
        assertTrue(
            "Failure in " + success + "\n" + result,
            result.getStdOut().contains(StringUtils.lines(success + ": SUCCESS")));
      }
    }
  }
}
