// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress.b72485384;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.regress.b72485384.GenericOuter.GenericInner;
import com.android.tools.r8.utils.AndroidApp;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class Regress72485384Test extends TestBase {

  @Parameters(name = "{0}")
  public static Collection<Object[]> getParameters() {
    String baseConfig =
        keepMainProguardConfiguration(Main.class)
            + "-keepattributes Signature,InnerClasses,EnclosingMethod ";
    return Arrays.asList(
        new Object[][] {
          {baseConfig, null},
          {baseConfig + "-dontshrink", null},
          {baseConfig + "-dontshrink -dontobfuscate", null},
          {baseConfig + "-dontobfuscate", null},
          {"", null},
          {"-dontshrink", null},
          {"-keep class DoesNotExist -dontshrink", "ClassNotFoundException"}
        });
  }

  private final static List<Class> CLASSES = ImmutableList
      .of(GenericOuter.class, GenericInner.class, Main.class);

  private final String proguardConfig;
  private final String expectedErrorMessage;

  public Regress72485384Test(String proguardConfig, String expectedErrorMessage) {
    this.proguardConfig = proguardConfig;
    this.expectedErrorMessage = expectedErrorMessage;
  }

  @Test
  public void testSignatureRewrite() throws Exception {
    AndroidApp app = compileWithR8(CLASSES, proguardConfig);
    if (expectedErrorMessage == null) {
      runOnArt(app, Main.class.getCanonicalName());
    } else {
      ToolHelper.ProcessResult result = runOnArtRaw(app, Main.class.getCanonicalName());
      assertThat(result.stderr, containsString(expectedErrorMessage));
    }
  }
}
