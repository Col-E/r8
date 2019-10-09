// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.corelib.conversionTests;

import static org.hamcrest.CoreMatchers.startsWith;

import com.android.tools.r8.utils.AndroidApiLevel;
import java.nio.file.Path;
import java.time.Clock;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.Test;

public class APIConversionLargeWarningTest extends APIConversionTestBase {

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
                "Desugared library API conversion: Generating a large wrapper for"
                    + " java.util.stream.Stream"))
        .assertNoInfoMessageThatMatches(
            startsWith(
                "Desugared library API conversion: Generating a large wrapper for java.time.Clock"))
        .assertNoInfoMessageThatMatches(
            startsWith(
                "Desugared library API conversion: Generating a large wrapper for"
                    + " java.util.function.Function"));
  }

  static class Executor {

    public static void main(String[] args) {
      CustomLibClass.callClock(Clock.systemUTC());
      CustomLibClass.callStream(Stream.empty());
      CustomLibClass.callFunction(x -> x);
    }
  }

  // This class will be put at compilation time as library and on the runtime class path.
  // This class is convenient for easy testing. Each method plays the role of methods in the
  // platform APIs for which argument/return values need conversion.
  static class CustomLibClass {

    public static void callStream(Stream stream) {}

    public static void callClock(Clock clock) {}

    public static void callFunction(Function<String, String> func) {}
  }
}
