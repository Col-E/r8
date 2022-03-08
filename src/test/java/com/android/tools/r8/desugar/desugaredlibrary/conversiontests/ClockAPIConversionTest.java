// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.conversiontests;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ClockAPIConversionTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final boolean shrinkDesugaredLibrary;
  private static final AndroidApiLevel MIN_SUPPORTED = AndroidApiLevel.O;
  private static final String EXPECTED_RESULT = StringUtils.lines("Z", "Z", "true", "Z", "Z");
  private static Path CUSTOM_LIB;

  @Parameters(name = "{0}, shrinkDesugaredLibrary: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getConversionParametersUpToExcluding(MIN_SUPPORTED), BooleanUtils.values());
  }

  public ClockAPIConversionTest(TestParameters parameters, boolean shrinkDesugaredLibrary) {
    this.shrinkDesugaredLibrary = shrinkDesugaredLibrary;
    this.parameters = parameters;
  }

  @BeforeClass
  public static void compileCustomLib() throws Exception {
    CUSTOM_LIB =
        testForD8(getStaticTemp())
            .addProgramClasses(CustomLibClass.class)
            .setMinApi(MIN_SUPPORTED)
            .compile()
            .writeToZip();
  }

  @Test
  public void testClockD8() throws Exception {
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForD8()
        .addLibraryFiles(getLibraryFile())
        .setMinApi(parameters.getApiLevel())
        .addProgramClasses(Executor.class)
        .addLibraryClasses(CustomLibClass.class)
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .assertNoMessages()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .addRunClasspathFiles(CUSTOM_LIB)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testClockR8() throws Exception {
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForR8(parameters.getBackend())
        .addLibraryFiles(getLibraryFile())
        .addKeepMainRule(Executor.class)
        .setMinApi(parameters.getApiLevel())
        .addProgramClasses(Executor.class)
        .addLibraryClasses(CustomLibClass.class)
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .addRunClasspathFiles(CUSTOM_LIB)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  static class Executor {

    @SuppressWarnings("ConstantConditions")
    public static void main(String[] args) {
      Clock clock1 = CustomLibClass.getClock();
      Clock localClock = Clock.systemUTC();
      Clock clock2 = CustomLibClass.mixClocks(localClock, Clock.systemUTC());
      System.out.println(clock1.getZone());
      System.out.println(clock2.getZone());
      System.out.println(localClock == clock2);
      System.out.println(CustomLibClass.getClocks()[0].getZone());
      System.out.println(CustomLibClass.getClockss()[0][0].getZone());
    }
  }

  // This class will be put at compilation time as library and on the runtime class path.
  // This class is convenient for easy testing. Each method plays the role of methods in the
  // platform APIs for which argument/return values need conversion.
  static class CustomLibClass {

    @SuppressWarnings("all")
    public static Clock getClock() {
      return Clock.systemUTC();
    }

    public static Clock[] getClocks() {
      return new Clock[] {Clock.systemUTC()};
    }

    public static Clock[][] getClockss() {
      return new Clock[][] {new Clock[] {Clock.systemUTC()}};
    }

    @SuppressWarnings("WeakerAccess")
    public static Clock mixClocks(Clock clock1, Clock clock2) {
      return clock1;
    }
  }
}
