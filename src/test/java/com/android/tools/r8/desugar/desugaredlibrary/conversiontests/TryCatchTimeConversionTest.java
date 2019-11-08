// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.conversiontests;

import com.android.tools.r8.TestRuntime.DexRuntime;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.time.ZoneId;
import org.junit.Test;

public class TryCatchTimeConversionTest extends APIConversionTestBase {

  @Test
  public void testBaseline() throws Exception {
    Path customLib = testForD8().addProgramClasses(CustomLibClass.class).compile().writeToZip();
    testForD8()
        .setMinApi(AndroidApiLevel.B)
        .addProgramClasses(BaselineExecutor.class)
        .addLibraryClasses(CustomLibClass.class)
        .enableCoreLibraryDesugaring(AndroidApiLevel.B)
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibraryWithConversionExtension, AndroidApiLevel.B)
        .addRunClasspathFiles(customLib)
        .run(new DexRuntime(DexVm.ART_9_0_0_HOST), BaselineExecutor.class)
        .assertSuccessWithOutput(StringUtils.lines("GMT", "GMT", "GMT", "GMT", "GMT"));
  }

  @Test
  public void testTryCatch() throws Exception {
    Path customLib = testForD8().addProgramClasses(CustomLibClass.class).compile().writeToZip();
    testForD8()
        .setMinApi(AndroidApiLevel.B)
        .addProgramClasses(TryCatchExecutor.class)
        .addLibraryClasses(CustomLibClass.class)
        .enableCoreLibraryDesugaring(AndroidApiLevel.B)
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibraryWithConversionExtension, AndroidApiLevel.B)
        .addRunClasspathFiles(customLib)
        .run(new DexRuntime(DexVm.ART_9_0_0_HOST), TryCatchExecutor.class)
        .assertSuccessWithOutput(
            StringUtils.lines("GMT", "GMT", "GMT", "GMT", "GMT", "Exception caught"));
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
