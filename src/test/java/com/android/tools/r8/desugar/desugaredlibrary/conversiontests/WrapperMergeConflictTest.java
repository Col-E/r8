// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.conversiontests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestRuntime.DexRuntime;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.ir.desugar.DesugaredLibraryWrapperSynthesizer;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import java.nio.file.Path;
import java.util.function.IntSupplier;
import org.junit.Test;

public class WrapperMergeConflictTest extends DesugaredLibraryTestBase {

  @Test
  public void testWrapperMergeConflict() throws Exception {
    Path customLib =
        testForD8()
            .addProgramClasses(CustomLibClass.class)
            .setMinApi(AndroidApiLevel.B)
            .compile()
            .writeToZip();
    Path path1 =
        testForD8()
            .addProgramClasses(Executor1.class)
            .addLibraryClasses(CustomLibClass.class)
            .setMinApi(AndroidApiLevel.B)
            .enableCoreLibraryDesugaring(AndroidApiLevel.B)
            .compile()
            .inspect(i -> this.assertWrappers(i, 2))
            .writeToZip();
    Path path2 =
        testForD8()
            .addProgramClasses(Executor2.class)
            .addLibraryClasses(CustomLibClass.class)
            .setMinApi(AndroidApiLevel.B)
            .enableCoreLibraryDesugaring(AndroidApiLevel.B)
            .compile()
            .inspect(i -> this.assertWrappers(i, 1))
            .writeToZip();
    testForD8()
        .addProgramFiles(path1, path2)
        .addLibraryClasses(CustomLibClass.class)
        .compile()
        .addDesugaredCoreLibraryRunClassPath(this::buildDesugaredLibrary, AndroidApiLevel.B)
        .inspect(this::assertBigWrappersPresent)
        .addRunClasspathFiles(customLib)
        .run(new DexRuntime(DexVm.ART_9_0_0_HOST), Executor1.class)
        .assertSuccessWithOutput(StringUtils.lines("3", "5"));
  }

  private void assertBigWrappersPresent(CodeInspector inspector) {
    assertWrappers(inspector, 2);
    inspector.allClasses().stream()
        .filter(
            c -> c.getOriginalName().startsWith(DesugaredLibraryWrapperSynthesizer.WRAPPER_PREFIX))
        .forEach(
            c ->
                assertTrue(
                    c.uniqueMethodWithName("convert")
                        .streamInstructions()
                        .anyMatch(InstructionSubject::isInstanceOf)));
  }

  private void assertWrappers(CodeInspector inspector, int num) {
    assertEquals(
        num,
        inspector.allClasses().stream()
            .filter(
                c ->
                    c.getOriginalName()
                        .startsWith(DesugaredLibraryWrapperSynthesizer.WRAPPER_PREFIX))
            .count());
  }

  static class Executor1 {

    public static void main(String[] args) {
      // Both wrappers are present.
      CustomLibClass.printInt(() -> 3);
      System.out.println(CustomLibClass.intSupplier().getAsInt());
    }
  }

  static class Executor2 {

    public static void main(String[] args) {
      // One wrapper is present.
      System.out.println(CustomLibClass.intSupplier().getAsInt());
    }
  }

  @SuppressWarnings("WeakerAccess")
  static class CustomLibClass {

    public static IntSupplier intSupplier() {
      return () -> 5;
    }

    public static void printInt(IntSupplier supplier) {
      System.out.println(supplier.getAsInt());
    }
  }
}
