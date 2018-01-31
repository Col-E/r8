// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress.b72485384;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.regress.b72485384.GenericOuter.GenericInner;
import com.android.tools.r8.utils.AndroidApp;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class Regress72485384Test extends TestBase {

  @Parameters(name = "{0}")
  public static List<String> getParameters() {
    String baseConfig =
        keepMainProguardConfiguration(Main.class)
            + "-keepattributes Signature,InnerClasses,EnclosingMethod ";
    return ImmutableList.of(
        baseConfig,
        baseConfig + "-dontshrink",
        baseConfig + "-dontshrink -dontobfuscate",
        baseConfig + "-dontobfuscate",
        "",
        "-dontshrink",
        "-keep class DoesNotExist -dontshrink"
    );
  }

  private final static List<Class> CLASSES = ImmutableList
      .of(GenericOuter.class, GenericInner.class, Main.class);

  private final String proguardConfig;

  public Regress72485384Test(String proguardConfig) {
    this.proguardConfig = proguardConfig;
  }

  @Test
  public void testSignatureRewrite() throws Exception {
    AndroidApp result = compileWithR8(CLASSES, proguardConfig);
    runOnArt(result, Main.class.getCanonicalName());
  }
}
