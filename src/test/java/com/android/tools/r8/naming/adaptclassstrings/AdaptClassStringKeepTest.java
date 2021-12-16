// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.adaptclassstrings;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.ProguardVersion;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AdaptClassStringKeepTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean isCompat;

  @Parameter(2)
  public ProguardVersion proguardVersion;

  @Parameters(name = "{0}, isCompat: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withSystemRuntime().build(),
        BooleanUtils.values(),
        ProguardVersion.values());
  }

  @Test
  public void testProguard() throws Exception {
    assumeTrue(isCompat);
    testForProguard(proguardVersion)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepRules("-adaptclassstrings")
        .addDontWarn(AdaptClassStringKeepTest.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("com.android.tools.r8.naming.adaptclassstrings.a")
        .inspect(inspector -> assertThat(inspector.clazz(Foo.class), isPresent()));
  }

  @Test
  public void testR8() throws Exception {
    assumeTrue(proguardVersion == ProguardVersion.getLatest());
    (isCompat ? testForR8Compat(parameters.getBackend()) : testForR8(parameters.getBackend()))
        .addInnerClasses(getClass())
        .setMinApi(AndroidApiLevel.B)
        .addKeepMainRule(Main.class)
        .addKeepRules("-adaptclassstrings")
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(Foo.class.getName())
        // TODO(b/210825389): We currently interpret -adaptclasstrings without pinning the class
        .inspect(inspector -> assertThat(inspector.clazz(Foo.class), isAbsent()));
  }

  public static class Foo {}

  public static class Main {

    public static void main(String[] args) {
      System.out.println(
          "com.android.tools.r8.naming.adaptclassstrings.AdaptClassStringKeepTest$Foo");
    }
  }
}
