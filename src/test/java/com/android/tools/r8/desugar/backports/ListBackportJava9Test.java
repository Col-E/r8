// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.backports;

import static com.android.tools.r8.utils.FileUtils.JAR_EXTENSION;
import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ListBackportJava9Test extends AbstractBackportTest {
  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK9)
        .withDexRuntimes()
        .withAllApiLevels()
        .build();
  }

  private static final Path TEST_JAR =
      Paths.get(ToolHelper.EXAMPLES_JAVA9_BUILD_DIR).resolve("backport" + JAR_EXTENSION);
  private static final String TEST_CLASS = "backport.ListBackportJava9Main";

  public ListBackportJava9Test(TestParameters parameters) {
    super(parameters, List.class, TEST_JAR, TEST_CLASS);
    // Note: None of the methods in this test exist in the latest android.jar. If/when they ship in
    // an actual API level, migrate these tests to ListBackportTest.

    // Available since API 1 and used to test created lists.
    ignoreInvokes("add");
    ignoreInvokes("get");
    ignoreInvokes("set");
    ignoreInvokes("size");
  }

  @Test
  public void desugaringApiLevelR() throws Exception {
    // TODO(154759404): This test should start to fail when testing on an Android R VM.
    if (parameters.getRuntime().isDex()
        && parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.Q)) {
      testForD8()
          .setMinApi(AndroidApiLevel.R)
          .addProgramClasses(MiniAssert.class, IgnoreInvokes.class)
          .addProgramFiles(TEST_JAR)
          .setIncludeClassesChecksum(true)
          .compile()
          .run(parameters.getRuntime(), TEST_CLASS)
          .assertFailureWithErrorThatMatches(containsString("java.lang.NoSuchMethodError"));
    }
  }
}
