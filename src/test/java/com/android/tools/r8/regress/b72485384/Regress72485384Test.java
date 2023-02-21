// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress.b72485384;


import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.regress.b72485384.GenericOuter.GenericInner;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class Regress72485384Test extends TestBase {

  @Parameters(name = "{1}, allowUnusedProguardConfigurationRules: {0}")
  public static Collection<Object[]> getParameters() {
    TestParametersCollection parametersCollection =
        getTestParameters()
            .withDexRuntimes()
            .withApiLevelsStartingAtIncluding(AndroidApiLevel.N)
            .build();
    List<Object[]> tests = new ArrayList<>();
    for (TestParameters parameters : parametersCollection) {
      Collections.addAll(
          tests,
          new Object[][] {
            {parameters, ""},
            {parameters, "-dontshrink"},
            {parameters, "-dontshrink -dontobfuscate"},
            {parameters, "-dontobfuscate"}
          });
    }
    return tests;
  }

  private final TestParameters parameters;
  private final String proguardConfig;

  public Regress72485384Test(TestParameters parameters, String proguardConfig) {
    this.parameters = parameters;
    this.proguardConfig =
        keepMainProguardConfiguration(Main.class)
            + "-keepattributes Signature,InnerClasses,EnclosingMethod "
            + proguardConfig;
  }

  @Test
  public void testSignatureRewrite() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(GenericOuter.class, GenericInner.class, Main.class)
        .addKeepRules(proguardConfig)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello World!", "Hello World!");
  }
}
