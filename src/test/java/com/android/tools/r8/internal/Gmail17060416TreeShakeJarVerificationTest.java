// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal;

import static com.android.tools.r8.ToolHelper.isLocalDevelopment;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.nio.file.Paths;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class Gmail17060416TreeShakeJarVerificationTest extends GmailCompilationBase {
  private static final int MAX_SIZE = 20000000;

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().build();
  }

  public Gmail17060416TreeShakeJarVerificationTest(TestParameters parameters) {
    super(170604, 16);
    this.parameters = parameters;
  }

  @Test
  public void buildAndTreeShakeFromDeployJar() throws Exception {
    assumeTrue(isLocalDevelopment());

    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addKeepRuleFiles(Paths.get(base).resolve(BASE_PG_CONF))
            .allowUnusedProguardConfigurationRules()
            .compile();

    int appSize = compileResult.app.applicationSize();
    assertTrue("Expected max size of " + MAX_SIZE+ ", got " + appSize, appSize < MAX_SIZE);
  }

}
