// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.conversiontests;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.time.Year;
import org.junit.Test;

public class APIConversionFinalClassErrorTest extends APIConversionTestBase {

  @Test
  public void testFinalMethod() {
    try {
      testForD8()
          .setMinApi(AndroidApiLevel.B)
          .addProgramClasses(Executor.class)
          .addLibraryClasses(CustomLibClass.class)
          .enableCoreLibraryDesugaring(AndroidApiLevel.B)
          .compileWithExpectedDiagnostics(this::assertDiagnosis);
      fail("Expected compilation error");
    } catch (CompilationFailedException ignored) {

    }
  }

  private void assertDiagnosis(TestDiagnosticMessages d) {
    assertEquals(
        "Cannot generate a wrapper for final class java.time.Year."
            + " Add a custom conversion in the desugared library.",
        d.getErrors().get(0).getDiagnosticMessage());
  }

  static class Executor {

    public static void main(String[] args) {
      System.out.println(CustomLibClass.call(Year.now()));
    }
  }

  // This class will be put at compilation time as library and on the runtime class path.
  // This class is convenient for easy testing. Each method plays the role of methods in the
  // platform APIs for which argument/return values need conversion.
  static class CustomLibClass {

    public static long call(Year year) {
      return 0L;
    }
  }
}
