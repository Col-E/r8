// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.backports;

import static com.android.tools.r8.utils.FileUtils.JAR_EXTENSION;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class LongBackportJava9Test extends AbstractBackportTest {
  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK9)
        .withDexRuntimes()
        .withAllApiLevelsAlsoForCf()
        .build();
  }

  private static final Path TEST_JAR =
      Paths.get(ToolHelper.EXAMPLES_JAVA9_BUILD_DIR).resolve("backport" + JAR_EXTENSION);

  public LongBackportJava9Test(TestParameters parameters) {
    super(parameters, Long.class, TEST_JAR, "backport.LongBackportJava9Main");
    // Note: The methods in this test exist in android.jar from Android T. When R8 builds targeting
    // Java 11 move these tests to LongBackportTest (out of examplesJava9).

    ignoreInvokes("toString");

    registerTarget(AndroidApiLevel.T, 17);
  }

  @Test
  @Override
  public void testD8() throws Exception {
    testD8(
        runResult ->
            runResult.applyIf(
                parameters.getDexRuntimeVersion().isEqualTo(Version.V6_0_1)
                    && parameters.getApiLevel().isGreaterThan(AndroidApiLevel.B),
                rr ->
                    rr.assertFailureWithErrorThatMatches(
                        // Sometimes the failure does not have the SIGSEGV printed, so check for
                        // the utils.cc file where the fault happens.
                        anyOf(containsString("SIGSEGV"), containsString("art/runtime/utils.cc"))),
                SingleTestRunResult::assertSuccess));
  }
}
