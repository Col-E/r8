// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal;

import static com.android.tools.r8.ToolHelper.isLocalDevelopment;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import java.nio.file.Paths;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class Gmail18082615TreeShakeJarVerificationTest extends GmailCompilationBase {
  private static final int MAX_SIZE = 20000000;

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().build();
  }

  public Gmail18082615TreeShakeJarVerificationTest(TestParameters parameters) {
    super(180826, 15);
    this.parameters = parameters;
  }

  @Test
  public void buildAndTreeShakeFromDeployJar() throws Exception {
    assumeTrue(isLocalDevelopment());

    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addKeepRuleFiles(
                Paths.get(base).resolve(BASE_PG_CONF),
                Paths.get(ToolHelper.PROGUARD_SETTINGS_FOR_INTERNAL_APPS, PG_CONF))
            .allowDiagnosticMessages()
            .allowUnusedProguardConfigurationRules()
            .compile()
            .assertAllInfoMessagesMatch(
                anyOf(
                    equalTo("Ignoring option: -optimizations"),
                    containsString("Proguard configuration rule does not match anything"),
                    containsString("Invalid parameter counts in MethodParameter attributes"),
                    containsString("Methods with invalid MethodParameter attributes")))
            .assertAllWarningMessagesMatch(containsString("Ignoring option:"));

    int appSize = compileResult.app.applicationSize();
    assertTrue("Expected max size of " + MAX_SIZE+ ", got " + appSize, appSize < MAX_SIZE);
  }
}
