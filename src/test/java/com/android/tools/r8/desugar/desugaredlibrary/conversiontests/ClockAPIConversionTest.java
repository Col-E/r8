// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.conversiontests;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.CustomLibrarySpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.time.Clock;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ClockAPIConversionTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  private static final AndroidApiLevel MIN_SUPPORTED = AndroidApiLevel.O;
  private static final String EXPECTED_RESULT =
      StringUtils.lines("Z", "Z", "true", "Z", "Z", "true", "true", "true", "true", "true", "true");
  private static final String DESUGARED_LIBRARY_EXPECTED_RESULT =
      StringUtils.lines(
          "Z", "Z", "true", "Z", "Z", "true", "true", "false", "false", "true", "true");

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getConversionParametersUpToExcluding(MIN_SUPPORTED),
        getJdk8Jdk11(),
        DEFAULT_SPECIFICATIONS);
  }

  public ClockAPIConversionTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @Test
  public void testClock() throws Throwable {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addProgramClasses(Executor.class)
        .setCustomLibrarySpecification(
            new CustomLibrarySpecification(CustomLibClass.class, MIN_SUPPORTED))
        .addKeepMainRule(Executor.class)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutput(DESUGARED_LIBRARY_EXPECTED_RESULT);
  }

  @Test
  public void testD8() throws Throwable {
    // Run a D8 test without desugared library on all runtimes which natively supports java.time to
    // ensure the expectations. The API level check is just to not run the same test repeatedly.
    assertEquals(AndroidApiLevel.O, MIN_SUPPORTED);
    assumeTrue(
        parameters.getApiLevel().isEqualTo(AndroidApiLevel.N_MR1)
            && parameters.isDexRuntime()
            && parameters.asDexRuntime().getVersion().isNewerThanOrEqual(Version.V8_1_0)
            && compilationSpecification == CompilationSpecification.D8_L8DEBUG);
    testForD8(parameters.getBackend())
        .addProgramClasses(Executor.class, CustomLibClass.class)
        .setMinApi(MIN_SUPPORTED)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testR8() throws Throwable {
    // Run a R8 test without desugared library on all runtimes which natively supports java.time to
    // ensure the expectations. The API level check is just to not run the same test repeatedly.
    assertEquals(AndroidApiLevel.O, MIN_SUPPORTED);
    assumeTrue(
        parameters.getApiLevel().isEqualTo(AndroidApiLevel.N_MR1)
            && parameters.isDexRuntime()
            && parameters.asDexRuntime().getVersion().isNewerThanOrEqual(Version.V8_1_0)
            && compilationSpecification == CompilationSpecification.D8_L8DEBUG);
    testForR8(parameters.getBackend())
        .addProgramClasses(Executor.class, CustomLibClass.class)
        .addKeepMainRule(Executor.class)
        .setMinApi(MIN_SUPPORTED)
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
      System.out.println(clock1.equals(CustomLibClass.getClock()));
      System.out.println(localClock.equals(Clock.systemUTC()));
      System.out.println(localClock.equals(clock1)); // Prints false with desugared library.
      System.out.println(clock1.equals(localClock)); // Prints false with desugared library.
      System.out.println(clock1.equals(CustomLibClass.getClocks()[0]));
      System.out.println(clock1.equals(CustomLibClass.getClockss()[0][0]));
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
