// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.rewrite.assertionerror;

import static com.android.tools.r8.ToolHelper.getDefaultAndroidJar;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AssertionErrorRewriteTest extends TestBase {

  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  private final TestParameters parameters;
  private final boolean expectCause;

  public AssertionErrorRewriteTest(TestParameters parameters) {
    this.parameters = parameters;

    // The exception cause is only preserved on API 16 and newer.
    expectCause =
        parameters.isCfRuntime()
            || parameters.getApiLevel().getLevel() >= AndroidApiLevel.J.getLevel();
  }

  @Test public void d8() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8()
        .addLibraryFiles(getDefaultAndroidJar())
        .addProgramClasses(Main.class)
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), Main.class, String.valueOf(expectCause))
        .assertSuccessWithOutputLines("OK", "OK");
  }

  @Test public void r8() throws Exception {
    testForR8(parameters.getBackend())
        .addLibraryFiles(getDefaultAndroidJar())
        .addProgramClasses(Main.class)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), Main.class, String.valueOf(expectCause))
        .assertSuccessWithOutputLines("OK", "OK");
  }

  public static final class Main {
    public static void main(String[] args) {
      boolean expectCause = Boolean.parseBoolean(args[0]);

      Throwable expectedCause = new RuntimeException("cause message");
      try {
        throwAssertionError(expectedCause);
        System.out.println("unreachable");
      } catch (AssertionError e) {
        String message = e.getMessage();
        if (!message.equals("message")) {
          throw new RuntimeException("Incorrect AssertionError message: " + message);
        } else {
          System.out.println("OK");
        }

        Throwable cause = e.getCause();
        if (expectCause && cause != expectedCause) {
          throw new RuntimeException("Incorrect AssertionError cause", cause);
        } else {
          System.out.println("OK");
        }
      }
    }

    @NeverInline
    private static void throwAssertionError(Throwable cause) {
      throw new AssertionError("message", cause);
    }
  }
}
