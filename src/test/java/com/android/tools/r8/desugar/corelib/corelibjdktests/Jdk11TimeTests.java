// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.corelib.corelibjdktests;

import static com.android.tools.r8.ToolHelper.JDK_TESTS_BUILD_DIR;
import static org.hamcrest.core.StringEndsWith.endsWith;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.D8TestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.corelib.CoreLibDesugarTestBase;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Paths;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class Jdk11TimeTests extends CoreLibDesugarTestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().build();
  }

  public Jdk11TimeTests(TestParameters parameters) {
    this.parameters = parameters;
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
        "test.java.util.TestFormatter.java"
      };
  // TODO(b/134732760): Investigate why these tests fail.
  private static String[] failuresToTriage =
      new String[] {
        "tck.java.time.TestIsoChronology",
        "test.java.time.TestClock_System",
        "test.java.time.chrono.TestChronologyPerf",
        "test.java.time.chrono.TestExampleCode",
        "test.java.time.chrono.TestChronoLocalDate",
        "test.java.time.chrono.TestUmmAlQuraChronology",
        "test.java.time.chrono.TestIsoChronoImpl",
        "test.java.time.chrono.TestEraDisplayName",
        "test.java.time.chrono.TestJapaneseChronology",
        "test.java.time.temporal.TestIsoWeekFields",
        "test.java.time.format.TestTextParserWithLocale",
        "test.java.time.format.TestUnicodeExtension",
        "test.java.time.format.TestDateTimeFormatterBuilderWithLocale",
        "test.java.time.format.TestNarrowMonthNamesAndDayNames",
        "test.java.time.format.TestTextPrinterWithLocale",
        "test.java.time.format.TestDateTimeFormatter",
        "test.java.time.format.TestReducedPrinter",
        "test.java.time.format.TestCharLiteralParser",
        "test.java.time.TestLocalDate"
      };
  private static String[] successes =
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
        "test.java.time.TestOffsetDateTime_instants",
      };

  @Test
  public void testTime() throws Exception {
    Assume.assumeTrue("TODO(b/134732760): Fix Android 7+.", requiresCoreLibDesugaring(parameters));
    String verbosity = "0";
    String[] mainParametersSuccesses = new String[successes.length + 1];
    mainParametersSuccesses[0] = verbosity;
    System.arraycopy(successes, 0, mainParametersSuccesses, 1, successes.length);
    D8TestCompileResult compileResult =
        testForD8()
            .addProgramFiles(Paths.get(JDK_TESTS_BUILD_DIR + "jdk11TimeTests.jar"))
            .addProgramFiles(Paths.get(JDK_TESTS_BUILD_DIR + "testng-6.10.jar"))
            .addProgramFiles(Paths.get(JDK_TESTS_BUILD_DIR + "jcommander-1.48.jar"))
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
            .setMinApi(parameters.getRuntime())
            .addOptionsModification(this::configureCoreLibDesugarForProgramCompilation)
            .compile()
            .addRunClasspathFiles(buildDesugaredLibrary(parameters.getRuntime()));
    for (String success : successes) {
      D8TestRunResult result =
          compileResult.run(parameters.getRuntime(), "TestNGMainRunner", verbosity, success);
      if (parameters.getRuntime().asDex().getVm().getVersion() == Version.V4_0_4) {
        result.assertSuccessWithOutputThatMatches(
            endsWith(StringUtils.lines(success + ": SUCCESS")));
      } else {
        // TODO(b/134732760): flaky depending on Android versions, triage other versions.
      }
    }
    for (String failure : failuresToTriage) {
      // TODO(b/134732760): Investigate and fix these failures, fails for various reasons depending
      // on Android versions (flaky).
      D8TestRunResult result =
          compileResult.run(parameters.getRuntime(), "TestNGMainRunner", verbosity, failure);
      assertTrue(
          endsWith(StringUtils.lines(failure + ": ERROR")).matches(result.getStdOut())
              || endsWith(StringUtils.lines(failure + ": FAILURE")).matches(result.getStdOut())
              || !(result.getStdErr().isEmpty()));
    }
  }
}
