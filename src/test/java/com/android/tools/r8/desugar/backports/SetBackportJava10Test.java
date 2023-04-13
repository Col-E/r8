// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
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
import java.util.Set;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SetBackportJava10Test extends AbstractBackportTest {
  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK10)
        .withDexRuntimes()
        .withAllApiLevelsAlsoForCf()
        .build();
  }

  private static final Path TEST_JAR =
      Paths.get(ToolHelper.EXAMPLES_JAVA10_BUILD_DIR).resolve("backport" + JAR_EXTENSION);

  public SetBackportJava10Test(TestParameters parameters) {
    super(parameters, Set.class, TEST_JAR, "backport.SetBackportJava10Main");
    // Note: None of the methods in this test exist in the latest android.jar. If/when they ship in
    // an actual API level, migrate these tests to SetBackportTest.

    // Available since API 1 and used to test created sets.
    ignoreInvokes("add");
    ignoreInvokes("contains");
    ignoreInvokes("size");

    // Set.of added in API 30
    registerTarget(AndroidApiLevel.R, 1);
    // Set.copyOf added in API 31
    registerTarget(AndroidApiLevel.S, 5);
  }
}
