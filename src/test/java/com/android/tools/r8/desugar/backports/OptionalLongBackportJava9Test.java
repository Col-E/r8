// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.backports;

import static com.android.tools.r8.utils.FileUtils.JAR_EXTENSION;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.OptionalLong;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class OptionalLongBackportJava9Test extends AbstractBackportTest {
  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return getTestParameters()
        .withDexRuntimesStartingFromIncluding(Version.V7_0_0)
        .withApiLevelsStartingAtIncluding(AndroidApiLevel.N)
        .withCfRuntimesStartingFromIncluding(CfVm.JDK9)
        .enableApiLevelsForCf()
        .build();
  }

  private static final Path TEST_JAR =
      Paths.get(ToolHelper.EXAMPLES_JAVA9_BUILD_DIR).resolve("backport" + JAR_EXTENSION);

  public OptionalLongBackportJava9Test(TestParameters parameters) {
    super(parameters, OptionalLong.class, TEST_JAR, "backport.OptionalLongBackportJava9Main");
    // Note: The methods in this test exist in android.jar from Android T. When R8 builds targeting
    // Java 11 move these tests to OptionalBackportTest (out of examplesJava9).

    // Available since N.
    ignoreInvokes("empty");
    ignoreInvokes("getAsLong");
    ignoreInvokes("isPresent");
    ignoreInvokes("of");

    registerTarget(AndroidApiLevel.T, 4);
  }
}
