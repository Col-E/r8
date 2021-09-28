// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.jdktests;

import static com.android.tools.r8.ToolHelper.JDK_TESTS_BUILD_DIR;
import static com.android.tools.r8.utils.FileUtils.CLASS_EXTENSION;
import static com.android.tools.r8.utils.FileUtils.JAVA_EXTENSION;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.D8TestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class Jdk11TimeTests extends Jdk11DesugaredLibraryTestBase {

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
            .withApiLevel(AndroidApiLevel.N)
            .build());
  }

  public Jdk11TimeTests(boolean shrinkDesugaredLibrary, TestParameters parameters) {
    this.shrinkDesugaredLibrary = shrinkDesugaredLibrary;
    this.parameters = parameters;
  }

  private static final Path JDK_11_TCK_TEST_FILES_DIR =
      Paths.get(ToolHelper.JDK_11_TIME_TESTS_DIR).resolve("tck");
  private static final Path JDK_11_TIME_TEST_FILES_DIR =
      Paths.get(ToolHelper.JDK_11_TIME_TESTS_DIR).resolve("test");
  private static final String JDK_11_TIME_TEST_EXCLUDE = "TestZoneTextPrinterParser.java";
  private static Path[] JDK_11_TIME_TEST_COMPILED_FILES;

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
  public static void compileJdk11StreamTests() throws Exception {
    Path tmpDirectory = getStaticTemp().newFolder("time").toPath();
    List<String> options =
        Arrays.asList(
            "--add-reads",
            "java.base=ALL-UNNAMED",
            "--patch-module",
            "java.base=" + JDK_11_JAVA_BASE_EXTENSION_CLASSES_DIR);
    javac(TestRuntime.getCheckedInJdk11(), getStaticTemp())
        .addOptions(options)
        .addClasspathFiles(
            ImmutableList.of(
                Paths.get(JDK_TESTS_BUILD_DIR + "testng-6.10.jar"),
                Paths.get(JDK_TESTS_BUILD_DIR + "jcommander-1.48.jar")))
        .addSourceFiles(getJdk11TimeTestFiles())
        .setOutputPath(tmpDirectory)
        .compile();
    JDK_11_TIME_TEST_COMPILED_FILES =
        getAllFilesWithSuffixInDirectory(tmpDirectory, CLASS_EXTENSION);
    assert JDK_11_TIME_TEST_COMPILED_FILES.length > 0;
  }

  // Following tests are also failing on the Bazel build, they cannot be run easily on
  // Android (difference in time precision, iAndroid printing, etc.).
  private static String[] wontFixFailures =
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
      };
  private static String[] formattingProblem =
      new String[] {
        "test.java.time.format.TestNarrowMonthNamesAndDayNames",
      };
  private static String[] successes = new String[] {
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
        "test.java.time.temporal.TestDateTimeBuilderCombinations",
        "test.java.time.temporal.TestJulianFields",
        "test.java.time.temporal.TestChronoUnit",
        "test.java.time.temporal.TestDateTimeValueRange",
        "test.java.time.TestClock_Fixed",
        "test.java.time.TestYear",
        "test.java.time.TestLocalTime",
        "test.java.time.TestZoneId",
        "test.java.time.TestOffsetTime",
        "test.java.time.TestClock_Offset",
        "test.java.time.TestPeriod",
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
        "test.java.time.format.TestDateTimeTextProviderWithLocale",
        "test.java.time.format.TestSettingsParser",
        "test.java.time.format.TestNumberParser",
        "test.java.time.format.TestTextParserWithLocale",
        "test.java.time.format.TestTextPrinterWithLocale",
        "test.java.time.format.TestReducedPrinter",
        "test.java.time.format.TestCharLiteralParser",
        "test.java.time.TestOffsetDateTime_instants",
        "test.java.time.chrono.TestChronologyPerf",
        "test.java.time.chrono.TestExampleCode",
        "test.java.time.chrono.TestJapaneseChronology",
        "test.java.time.chrono.TestChronoLocalDate",
        "test.java.time.chrono.TestIsoChronoImpl",
        "tck.java.time.TestIsoChronology",
        "test.java.time.TestClock_System",
        "test.java.time.chrono.TestUmmAlQuraChronology",
        "test.java.time.temporal.TestIsoWeekFields",
        "test.java.time.format.TestUnicodeExtension",
        "test.java.time.format.TestDateTimeFormatterBuilderWithLocale",
      };

  @Test
  public void testTime() throws Exception {
    if (parameters.getRuntime().asDex().getVm().isEqualTo(DexVm.ART_12_0_0_HOST)) {
      // TODO(b/197078988): Many additional tests fails with Android 12.
      return;
    }
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    String verbosity = "2";
    D8TestCompileResult compileResult =
        testForD8()
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
            .addProgramFiles(getPathsFiles())
            .addProgramFiles(JDK_11_TIME_TEST_COMPILED_FILES)
            .addProgramFiles(Paths.get(JDK_TESTS_BUILD_DIR + "testng-6.10.jar"))
            .addProgramFiles(Paths.get(JDK_TESTS_BUILD_DIR + "jcommander-1.48.jar"))
            .addProgramFiles(
                Paths.get(
                    ToolHelper.JAVA_CLASSES_DIR + "examplesTestNGRunner/TestNGMainRunner.class"))
            .setMinApi(parameters.getApiLevel())
            .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
            .compile()
            .withArt6Plus64BitsLib()
            .addDesugaredCoreLibraryRunClassPath(
                this::buildDesugaredLibrary,
                parameters.getApiLevel(),
                keepRuleConsumer.get(),
                shrinkDesugaredLibrary);
    for (String success : successes) {
      D8TestRunResult result =
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
    for (String issue : formattingProblem) {
      D8TestRunResult result =
          compileResult.run(parameters.getRuntime(), "TestNGMainRunner", verbosity, issue);
      if (requiresAnyCoreLibDesugaring(parameters)) {
        // Fails due to formatting differences in desugared library.
        assertTrue(result.getStdOut().contains("for style NARROW"));
      } else {
        assertTrue(result.getStdOut().contains(StringUtils.lines(issue + ": SUCCESS")));
      }
    }
  }
}
