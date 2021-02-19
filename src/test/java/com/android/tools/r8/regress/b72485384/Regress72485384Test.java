// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress.b72485384;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.regress.b72485384.GenericOuter.GenericInner;
import com.android.tools.r8.utils.AndroidApp;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class Regress72485384Test extends TestBase {
  @Rule public ExpectedException thrown = ExpectedException.none();

  @Parameters(name = "{1}, allowUnusedProguardConfigurationRules: {0}")
  public static Collection<Object[]> getParameters() {
    String baseConfig =
        keepMainProguardConfiguration(Main.class)
            + "-keepattributes Signature,InnerClasses,EnclosingMethod ";
    return Arrays.asList(
        new Object[][] {
          {false, baseConfig, null},
          {false, baseConfig + "-dontshrink", null},
          {false, baseConfig + "-dontshrink -dontobfuscate", null},
          {false, baseConfig + "-dontobfuscate", null},
          {true, "-keep class DoesNotExist -dontshrink", "ClassNotFoundException"}
        });
  }

  private static final List<Class<?>> CLASSES =
      ImmutableList.of(GenericOuter.class, GenericInner.class, Main.class);

  private final boolean allowUnusedProguardConfigurationRules;
  private final String proguardConfig;
  private final String expectedErrorMessage;

  public Regress72485384Test(
      boolean allowUnusedProguardConfigurationRules,
      String proguardConfig,
      String expectedErrorMessage) {
    this.allowUnusedProguardConfigurationRules = allowUnusedProguardConfigurationRules;
    this.proguardConfig = proguardConfig;
    this.expectedErrorMessage = expectedErrorMessage;
  }

  @Test
  public void testSignatureRewrite() throws Exception {
    AndroidApp app =
        testForR8(Backend.DEX)
            .addProgramClasses(CLASSES)
            .addKeepRules(proguardConfig)
            .allowUnusedProguardConfigurationRules(allowUnusedProguardConfigurationRules)
            .compile()
            .getApp();

    if (expectedErrorMessage == null) {
      if (ToolHelper.getDexVm().getVersion().isOlderThanOrEqual(ToolHelper.DexVm.Version.V6_0_1)) {
        // Resolution of java.util.function.Function fails.
        thrown.expect(AssertionError.class);
      }

      runOnArt(app, Main.class.getCanonicalName());
    } else {
      ToolHelper.ProcessResult result = runOnArtRaw(app, Main.class.getCanonicalName());
      assertThat(result.stderr, containsString(expectedErrorMessage));
    }
  }
}
