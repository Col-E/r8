// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.conversiontests;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.conversiontests.SummaryStatisticsConversionTest.CustomLibClass;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TryCatchTimeConversionTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final boolean shrinkDesugaredLibrary;
  private static final AndroidApiLevel MIN_SUPPORTED = AndroidApiLevel.O;
  private static final String EXPECTED_RESULT =
      StringUtils.lines("GMT", "GMT", "GMT", "GMT", "GMT");
  private static final String EXPECTED_RESULT_EXCEPTION =
      StringUtils.lines("GMT", "GMT", "GMT", "GMT", "GMT", "Exception caught");
  private static Path CUSTOM_LIB;

  @Parameters(name = "{0}, shrinkDesugaredLibrary: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getConversionParametersUpToExcluding(MIN_SUPPORTED), BooleanUtils.values());
  }

  public TryCatchTimeConversionTest(TestParameters parameters, boolean shrinkDesugaredLibrary) {
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
  public void testBaselineD8() throws Exception {
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForD8()
        .setMinApi(parameters.getApiLevel())
        .addProgramClasses(BaselineExecutor.class)
        .addLibraryClasses(CustomLibClass.class)
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .addRunClasspathFiles(CUSTOM_LIB)
        .run(parameters.getRuntime(), BaselineExecutor.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testBaselineR8() throws Exception {
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForR8(parameters.getBackend())
        .setMinApi(parameters.getApiLevel())
        .addProgramClasses(BaselineExecutor.class)
        .addKeepMainRule(BaselineExecutor.class)
        .addLibraryClasses(CustomLibClass.class)
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .addRunClasspathFiles(CUSTOM_LIB)
        .run(parameters.getRuntime(), BaselineExecutor.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testTryCatchD8() throws Exception {
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForD8()
        .setMinApi(parameters.getApiLevel())
        .addProgramClasses(TryCatchExecutor.class)
        .addLibraryClasses(CustomLibClass.class)
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .addRunClasspathFiles(CUSTOM_LIB)
        .run(parameters.getRuntime(), TryCatchExecutor.class)
        .assertSuccessWithOutput(EXPECTED_RESULT_EXCEPTION);
  }

  @Test
  public void testTryCatchR8() throws Exception {
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForR8(parameters.getBackend())
        .setMinApi(parameters.getApiLevel())
        .addProgramClasses(TryCatchExecutor.class)
        .addKeepMainRule(TryCatchExecutor.class)
        .addLibraryClasses(CustomLibClass.class)
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .addRunClasspathFiles(CUSTOM_LIB)
        .run(parameters.getRuntime(), TryCatchExecutor.class)
        .assertSuccessWithOutput(EXPECTED_RESULT_EXCEPTION);
  }

  @SuppressWarnings("WeakerAccess")
  static class BaselineExecutor {

    private static final String ZONE_ID = "GMT";

    public static void main(String[] args) {
      returnOnly();
      oneParameter();
      twoParameters();
      oneParameterReturn();
      twoParametersReturn();
    }

    public static void returnOnly() {
      ZoneId returnOnly = CustomLibClass.returnOnly();
      System.out.println(returnOnly.getId());
    }

    public static void oneParameterReturn() {
      ZoneId z1 = ZoneId.of(ZONE_ID);
      ZoneId oneParam = CustomLibClass.oneParameterReturn(z1);
      System.out.println(oneParam.getId());
    }

    public static void twoParametersReturn() {
      ZoneId z1 = ZoneId.of(ZONE_ID);
      ZoneId z2 = ZoneId.of(ZONE_ID);
      ZoneId twoParam = CustomLibClass.twoParametersReturn(z1, z2);
      System.out.println(twoParam.getId());
    }

    public static void oneParameter() {
      ZoneId z1 = ZoneId.of(ZONE_ID);
      String res = CustomLibClass.oneParameter(z1);
      System.out.println(res);
    }

    public static void twoParameters() {
      ZoneId z1 = ZoneId.of(ZONE_ID);
      ZoneId z2 = ZoneId.of(ZONE_ID);
      String res = CustomLibClass.twoParameters(z1, z2);
      System.out.println(res);
    }
  }

  @SuppressWarnings("WeakerAccess")
  static class TryCatchExecutor {

    private static final String ZONE_ID = "GMT";

    public static void main(String[] args) {
      returnOnly();
      oneParameter();
      twoParameters();
      oneParameterReturn();
      twoParametersReturn();
      twoParametersThrow();
    }

    public static void returnOnly() {
      ZoneId returnOnly;
      try {
        returnOnly = CustomLibClass.returnOnly();
      } catch (Exception e) {
        throw new RuntimeException("Test failed.");
      }
      System.out.println(returnOnly.getId());
    }

    public static void oneParameterReturn() {
      ZoneId z1 = ZoneId.of(ZONE_ID);
      ZoneId oneParam;
      try {
        oneParam = CustomLibClass.oneParameterReturn(z1);
      } catch (Exception e) {
        throw new RuntimeException("Test failed.");
      }
      System.out.println(oneParam.getId());
    }

    public static void twoParametersReturn() {
      ZoneId z1 = ZoneId.of(ZONE_ID);
      ZoneId z2 = ZoneId.of(ZONE_ID);
      ZoneId twoParam;
      try {
        twoParam = CustomLibClass.twoParametersReturn(z1, z2);
      } catch (Exception e) {
        throw new RuntimeException("Test failed.");
      }
      System.out.println(twoParam.getId());
    }

    public static void oneParameter() {
      ZoneId z1 = ZoneId.of(ZONE_ID);
      String res;
      try {
        res = CustomLibClass.oneParameter(z1);
      } catch (Exception e) {
        throw new RuntimeException("Test failed.");
      }
      System.out.println(res);
    }

    public static void twoParameters() {
      ZoneId z1 = ZoneId.of(ZONE_ID);
      ZoneId z2 = ZoneId.of(ZONE_ID);
      String res;
      try {
        res = CustomLibClass.twoParameters(z1, z2);
      } catch (Exception e) {
        throw new RuntimeException("Test failed.");
      }
      System.out.println(res);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void twoParametersThrow() {
      ZoneId z1 = ZoneId.of(ZONE_ID);
      ZoneId z2 = ZoneId.of(ZONE_ID);
      try {
        CustomLibClass.twoParametersThrow(z1, z2);
        throw new RuntimeException("Test failed.");
      } catch (ArithmeticException e) {
        System.out.println("Exception caught");
      }
    }
  }

  // This class will be put at compilation time as library and on the runtime class path.
  // This class is convenient for easy testing. Each method plays the role of methods in the
  // platform APIs for which argument/return values need conversion.
  @SuppressWarnings("WeakerAccess")
  static class CustomLibClass {

    private static final String ZONE_ID = "GMT";

    public static ZoneId returnOnly() {
      return ZoneId.of(ZONE_ID);
    }

    public static ZoneId oneParameterReturn(ZoneId z1) {
      return z1;
    }

    public static ZoneId twoParametersReturn(ZoneId z1, ZoneId z2) {
      return z1;
    }

    public static String oneParameter(ZoneId z1) {
      return z1.getId();
    }

    public static String twoParameters(ZoneId z1, ZoneId z2) {
      return z1.getId();
    }

    @SuppressWarnings({"divzero", "NumericOverflow", "UnusedReturnValue"})
    public static String twoParametersThrow(ZoneId z1, ZoneId z2) {
      return "" + (1 / 0);
    }
  }
}
