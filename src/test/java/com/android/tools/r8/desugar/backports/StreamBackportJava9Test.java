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
import java.util.stream.Stream;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class StreamBackportJava9Test extends AbstractBackportTest {
  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return getTestParameters()
        .withDexRuntimesStartingFromIncluding(Version.V7_0_0)
        .withApiLevelsStartingAtIncluding(AndroidApiLevel.N)
        .withCfRuntimesStartingFromIncluding(CfVm.JDK9)
        .build();
  }

  private static final Path TEST_JAR =
      Paths.get(ToolHelper.EXAMPLES_JAVA9_BUILD_DIR).resolve("backport" + JAR_EXTENSION);

  public StreamBackportJava9Test(TestParameters parameters) {
    super(parameters, Stream.class, TEST_JAR, "backport.StreamBackportJava9Main");
    // Note: The methods in this test exist from Android U. However, they are only available from
    // Java 9. When tests build with Java 9 migrate to StreamBackportTest and add insert
    // StreamBackportJava9Main as an inner class here.

    // Available since N as part of library desugaring.
    ignoreInvokes("of");
    ignoreInvokes("empty");
    ignoreInvokes("count");

    registerTarget(AndroidApiLevel.U, 2);
  }
}
