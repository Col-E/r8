// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.corelib.conversionTests;

import static org.junit.Assert.assertEquals;
import com.android.tools.r8.TestRuntime.DexRuntime;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.ir.desugar.DesugaredLibraryWrapperSynthesizer;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.Test;

public class WrapperMergeTest extends APIConversionTestBase {

  @Test
  public void testWrapperMerge() throws Exception {
    // Multiple wrapper classes have to be merged here.
    Path path1 = testForD8()
        .addProgramClasses(Executor1.class)
        .setMinApi(AndroidApiLevel.B)
        .enableCoreLibraryDesugaring(AndroidApiLevel.B)
        .compile()
        .inspect(this::assertWrappers)
        .writeToZip();
    Path path2 = testForD8()
        .addProgramClasses(Executor2.class)
        .setMinApi(AndroidApiLevel.B)
        .enableCoreLibraryDesugaring(AndroidApiLevel.B)
        .compile()
        .inspect(this::assertWrappers)
        .writeToZip();
    testForD8()
        .addProgramFiles(path1,path2)
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibraryWithConversionExtension, AndroidApiLevel.B)
        .run(new DexRuntime(DexVm.ART_9_0_0_HOST), Executor1.class)
        .assertSuccessWithOutput(
            StringUtils.lines("[1, 2, 3]"));

  }

  private void assertWrappers(CodeInspector inspector) {
    assertEquals(2,inspector.allClasses().stream().filter(c -> c.getOriginalName().contains(
        DesugaredLibraryWrapperSynthesizer.WRAPPER_PREFIX)).count());
  }

  static class Executor1 {

    public static void main(String[] args) {
      int[] ints = new int[3];
      Arrays.setAll(ints,x->x+1);
      System.out.println(Arrays.toString(ints));
    }
  }

  static class Executor2 {

    public static void main(String[] args) {
      int[] ints = new int[3];
      Arrays.setAll(ints,x->x+2);
      System.out.println(Arrays.toString(ints));
    }
  }

}
