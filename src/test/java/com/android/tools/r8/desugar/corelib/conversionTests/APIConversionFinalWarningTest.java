// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.corelib.conversionTests;

import static org.hamcrest.CoreMatchers.startsWith;

import com.android.tools.r8.TestRuntime.DexRuntime;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.util.LongSummaryStatistics;
import org.junit.Test;

public class APIConversionFinalWarningTest extends APIConversionTestBase {

  @Test
  public void testFinalMethod() throws Exception {
    Path customLib = testForD8().addProgramClasses(CustomLibClass.class).compile().writeToZip();
    testForD8()
        .setMinApi(AndroidApiLevel.B)
        .addProgramClasses(Executor.class)
        .addLibraryClasses(CustomLibClass.class)
        .enableCoreLibraryDesugaring(AndroidApiLevel.B)
        .compile()
        .assertInfoMessageThatMatches(
            startsWith(
                "Desugared library API conversion: cannot wrap final methods"
                    + " [java.util.LongSummaryStatistics"))
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibraryWithConversionExtension, AndroidApiLevel.B)
        .addRunClasspathFiles(customLib)
        .run(new DexRuntime(DexVm.ART_9_0_0_HOST), Executor.class)
        .assertSuccessWithOutput(
            StringUtils.lines(
                "Unsupported conversion for java.util.LongSummaryStatistics. See compilation time"
                    + " infos for more details."));
  }

  static class Executor {

    public static void main(String[] args) {
      LongSummaryStatistics statistics = new LongSummaryStatistics();
      statistics.accept(3L);
      try {
        makeCall(statistics);
      } catch (RuntimeException e) {
        System.out.println(e.getMessage());
      }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    static void makeCall(LongSummaryStatistics statistics) {
      CustomLibClass.call(statistics);
    }
  }

  // This class will be put at compilation time as library and on the runtime class path.
  // This class is convenient for easy testing. Each method plays the role of methods in the
  // platform APIs for which argument/return values need conversion.
  static class CustomLibClass {

    public static long call(LongSummaryStatistics stats) {
      return stats.getMax();
    }
  }
}
