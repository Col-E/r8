// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.conversiontests;

import com.android.tools.r8.TestRuntime.DexRuntime;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import org.junit.Test;

public class UnwrapConversionTest extends DesugaredLibraryTestBase {

  @Test
  public void testUnwrap() throws Exception {
    Path customLib = testForD8().addProgramClasses(CustomLibClass.class).compile().writeToZip();
    testForD8()
        .setMinApi(AndroidApiLevel.B)
        .addProgramClasses(Executor.class)
        .addLibraryClasses(CustomLibClass.class)
        .enableCoreLibraryDesugaring(AndroidApiLevel.B)
        .compile()
        .addDesugaredCoreLibraryRunClassPath(this::buildDesugaredLibrary, AndroidApiLevel.B)
        .addRunClasspathFiles(customLib)
        .run(new DexRuntime(DexVm.ART_9_0_0_HOST), Executor.class)
        .assertSuccessWithOutput(StringUtils.lines("true", "true"));
  }

  static class Executor {

    @SuppressWarnings("all")
    public static void main(String[] args) {
      // Type wrapper.
      IntConsumer intConsumer = i -> {};
      IntConsumer unwrappedIntConsumer = CustomLibClass.identity(intConsumer);
      System.out.println(intConsumer == unwrappedIntConsumer);

      // Vivified wrapper.
      DoubleConsumer consumer = CustomLibClass.getConsumer();
      System.out.println(CustomLibClass.testConsumer(consumer));
    }
  }

  // This class will be put at compilation time as library and on the runtime class path.
  // This class is convenient for easy testing. Each method plays the role of methods in the
  // platform APIs for which argument/return values need conversion.
  static class CustomLibClass {

    private static DoubleConsumer consumer = d -> {};

    @SuppressWarnings("WeakerAccess")
    public static IntConsumer identity(IntConsumer intConsumer) {
      return intConsumer;
    }

    public static DoubleConsumer getConsumer() {
      return consumer;
    }

    @SuppressWarnings("WeakerAccess")
    public static boolean testConsumer(DoubleConsumer doubleConsumer) {
      return doubleConsumer == consumer;
    }
  }
}
