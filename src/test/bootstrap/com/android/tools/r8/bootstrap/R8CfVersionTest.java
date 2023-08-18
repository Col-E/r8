// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.bootstrap;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class R8CfVersionTest extends TestBase {

  private final CfVersion targetVersion = CfVersion.V11;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public R8CfVersionTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void testCfVersionR8() throws IOException {
    CodeInspector inspector = new CodeInspector(ToolHelper.getR8WithRelocatedDeps());
    inspector.forAllClasses(
        clazz -> {
          assertTrue(
              clazz
                  .getDexProgramClass()
                  .getInitialClassFileVersion()
                  .isLessThanOrEqualTo(targetVersion));
        });
  }

  @Test
  public void testCfVersionR8Lib() throws IOException {
    // Only run when testing R8 lib as only then do we know it is built and up-to-date.
    assumeTrue(ToolHelper.isTestingR8Lib());
    CodeInspector inspector = new CodeInspector(ToolHelper.R8LIB_JAR);
    inspector.forAllClasses(
        clazz -> {
          assertTrue(
              clazz
                  .getDexProgramClass()
                  .getInitialClassFileVersion()
                  .isLessThanOrEqualTo(targetVersion));
        });
  }
}
