// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.backports;

import static com.android.tools.r8.utils.FileUtils.JAR_EXTENSION;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class StrictMathBackportJava17Test extends AbstractBackportTest {

  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return getTestParameters()
        .withDexRuntimes()
        .withAllApiLevels()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK17)
        .build();
  }

  private static final Path TEST_JAR =
      Paths.get(ToolHelper.TESTS_BUILD_DIR).resolve("examplesJava17/backport" + JAR_EXTENSION);

  public StrictMathBackportJava17Test(TestParameters parameters) {
    super(parameters, StrictMath.class, TEST_JAR, "backport.StrictMathBackportJava17Main");
    registerTarget(AndroidApiLevel.U, 30);
  }
}
