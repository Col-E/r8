// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.desugaredlibrary;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class BackwardsCompatibleSpecificationTest extends TestBase {

  private static final List<String> RELEASES = ImmutableList.of("2.0.74");

  @Parameterized.Parameters(name = "{1}")
  public static List<Object[]> data() {
    return buildParameters(getTestParameters().withNoneRuntime().build(), RELEASES);
  }

  private final Path desugaredLib = ToolHelper.getDesugarJDKLibs();
  private final Path desugaredSpec = ToolHelper.DESUGAR_LIB_JSON_FOR_TESTING;
  private final String release;

  public BackwardsCompatibleSpecificationTest(TestParameters parameters, String release) {
    parameters.assertNoneRuntime();
    this.release = release;
  }

  private Path getReleaseJar() {
    return Paths.get(ToolHelper.THIRD_PARTY_DIR, "r8-releases", release, "r8lib.jar");
  }

  @Test
  public void test() throws Exception {
    ProcessResult result =
        ToolHelper.runJava(
            getReleaseJar(),
            "com.android.tools.r8.L8",
            "--desugared-lib",
            desugaredSpec.toString(),
            desugaredLib.toString());
    assertEquals(result.toString(), 0, result.exitCode);
  }
}
