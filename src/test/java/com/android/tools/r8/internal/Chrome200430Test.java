// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal;

import static com.android.tools.r8.ToolHelper.isLocalDevelopment;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class Chrome200430Test extends ChromeCompilationBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public Chrome200430Test(TestParameters parameters) {
    super(200430, false);
    parameters.assertNoneRuntime();
  }

  @Test
  public void test() throws Exception {
    assumeTrue(isLocalDevelopment());
    testForR8(Backend.DEX)
        .addProgramFiles(getProgramFiles())
        .addLibraryFiles(getLibraryFiles())
        .addKeepRuleFiles(getKeepRuleFiles())
        .allowUnusedProguardConfigurationRules()
        .setMinApi(AndroidApiLevel.N)
        .compile();
  }
}
