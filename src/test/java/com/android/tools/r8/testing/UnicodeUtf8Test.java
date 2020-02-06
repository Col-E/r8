// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.testing;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/* This test is ensuring that javac will compile expecting an UTF-8 encoding of the test. */
@RunWith(Parameterized.class)
public class UnicodeUtf8Test extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public UnicodeUtf8Test(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRuntime() throws ExecutionException, CompilationFailedException, IOException {
    testForRuntime(parameters)
        .addProgramClasses(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("\uD83C\uDCA1");
  }

  public static class Main {

    private static final String MOTOR_HEAD = "🂡";

    public static void main(String[] args) throws UnsupportedEncodingException {
      (new PrintStream(System.out, true, "UTF-8")).println(MOTOR_HEAD);
    }
  }
}
