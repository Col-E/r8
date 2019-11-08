// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.conversiontests;

import com.android.tools.r8.TestRuntime.DexRuntime;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.time.Clock;
import org.junit.Test;

public class ClockAPIConversionTest extends APIConversionTestBase {

  @Test
  public void testClock() throws Exception {
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
        .assertSuccessWithOutput(StringUtils.lines("Z", "Z", "true"));
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

    @SuppressWarnings("WeakerAccess")
    public static Clock mixClocks(Clock clock1, Clock clock2) {
      return clock1;
    }
  }
}
