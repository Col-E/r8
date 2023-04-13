// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.backports;

import static com.android.tools.r8.utils.FileUtils.JAR_EXTENSION;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class CharacterBackportJava11Test extends AbstractBackportTest {
  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return getTestParameters()
        .withDexRuntimes()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK11)
        .withAllApiLevelsAlsoForCf()
        .build();
  }

  private static final Path TEST_JAR =
      Paths.get(ToolHelper.EXAMPLES_JAVA11_JAR_DIR).resolve("backport" + JAR_EXTENSION);

  public CharacterBackportJava11Test(TestParameters parameters) {
    super(parameters, Character.class, TEST_JAR, "backport.CharacterBackportJava11Main");
    // Note: None of the methods in this test exist in the latest android.jar. If/when they ship in
    // an actual API level, migrate these tests to CharacterBackportTest.
  }
}
