// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.conversiontests;

import com.android.tools.r8.TestRuntime.DexRuntime;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.time.MonthDay;
import org.junit.Test;

// Longs and double take two stack indexes, this had to be dealt with in
// CfAPIConverter*WrapperCodeProvider (See stackIndex vs index), this class tests that the
// synthetic Cf code is correct.
public class BasicLongDoubleConversionTest extends APIConversionTestBase {

  @Test
  public void testRewrittenAPICalls() throws Exception {
    Path customLib = testForD8().addProgramClasses(CustomLibClass.class).compile().writeToZip();
    testForD8()
        .setMinApi(AndroidApiLevel.B)
        .addProgramClasses(Executor.class)
        .addLibraryClasses(CustomLibClass.class)
        .enableCoreLibraryDesugaring(AndroidApiLevel.B)
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibraryWithConversionExtension, AndroidApiLevel.B)
        .addRunClasspathFiles(customLib)
        .run(new DexRuntime(DexVm.ART_9_0_0_HOST), Executor.class)
        .assertSuccessWithOutput(StringUtils.lines("--01-16"));
  }

  static class Executor {

    public static void main(String[] args) {
      System.out.println(
          CustomLibClass.mix(3L, 4L, MonthDay.of(1, 2), 5.0, 6.0, MonthDay.of(10, 20)));
    }
  }

  // This class will be put at compilation time as library and on the runtime class path.
  // This class is convenient for easy testing. Each method plays the role of methods in the
  // platform APIs for which argument/return values need conversion.
  static class CustomLibClass {

    public static MonthDay mix(
        long l1, long l2, MonthDay monthDay1, double d1, double d2, MonthDay monthDay2) {
      return monthDay1.withDayOfMonth((int) (monthDay2.getDayOfMonth() + l1 - d1 + l2 - d2));
    }
  }
}
