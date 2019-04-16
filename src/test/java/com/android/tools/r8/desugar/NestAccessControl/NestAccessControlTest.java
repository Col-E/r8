// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.NestAccessControl;

import static com.android.tools.r8.utils.FileUtils.JAR_EXTENSION;
import static org.hamcrest.core.StringContains.containsString;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import java.nio.file.Paths;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NestAccessControlTest extends TestBase {

  static final String EXAMPLE_DIR = ToolHelper.EXAMPLES_JAVA11_BUILD_DIR;

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().build();
  }

  public NestAccessControlTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testNestAccessControl() throws Exception {
    testForD8()
        .addProgramFiles(Paths.get(EXAMPLE_DIR, "nestHostExample" + JAR_EXTENSION))
        .setMinApi(parameters.getRuntime())
        .compile()
        .run(parameters.getRuntime(), "NestHostExample")
        .assertFailureWithErrorThatMatches(containsString("IllegalAccessError"));
  }
}
