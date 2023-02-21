// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.rewrite.assertionerror;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
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

  @Test
  public void d8() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8()
        .addProgramClasses(Main.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        // None of the VMs we have for testing is missing the two args constructor.
        .assertSuccessWithOutputLines("message", "java.lang.RuntimeException: cause message");
  }

  @Test
  public void d8NoDesugar() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8()
        .addProgramClasses(Main.class)
        .setMinApi(parameters)
        .disableDesugaring()
        .compile()
        // TODO(b/247596495): There should be no synthetics.
        .inspect(
            inspector ->
                assertTrue(
                    inspector.allClasses().stream()
                        .noneMatch(
                            clazz ->
                                SyntheticItemsTestUtils.isExternalSynthetic(
                                    clazz.getFinalReference()))))
        .run(parameters.getRuntime(), Main.class)
        // None of the VMs we have for testing is missing the two args constructor.
        .assertSuccessWithOutputLines("message", "java.lang.RuntimeException: cause message");
  }

  @Test
  public void r8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class, String.valueOf(expectCause))
        // None of the VMs we have for testing is missing the two args constructor.
        .assertSuccessWithOutputLines("message", "java.lang.RuntimeException: cause message");
  }

  public static final class Main {
    public static void main(String[] args) {
      Throwable expectedCause = new RuntimeException("cause message");
      try {
        throwAssertionError(expectedCause);
        System.out.println("unreachable");
      } catch (AssertionError e) {
        System.out.println(e.getMessage());
        System.out.println(e.getCause());
      }
    }

    @NeverInline
    private static void throwAssertionError(Throwable cause) {
      throw new AssertionError("message", cause);
    }
  }
}
