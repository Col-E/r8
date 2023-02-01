// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.mappingcompose;

import static com.android.tools.r8.mappingcompose.ComposeTestHelpers.doubleToSingleQuote;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.naming.MappingComposer;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/***
 * This is a regression test for b/267289876.
 */
@RunWith(Parameterized.class)
public class ComposeNoMinifiedPositionForMethodTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public ComposeNoMinifiedPositionForMethodTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  private static final String mappingFoo =
      StringUtils.unixLines(
          "# {'id':'com.android.tools.r8.mapping','version':'2.2'}",
          "com.foo -> a:",
          "    void m1():13:13 -> x",
          "    1:1:android.MediaBrowserCompat$MediaItem[] newArray(int):42:42 -> y",
          "    java.lang.Object[] newArray(int):58 -> y");
  private static final String mappingBar =
      StringUtils.unixLines(
          "# {'id':'com.android.tools.r8.mapping','version':'2.2'}",
          "a -> b:",
          "    1:2:void x():5:5 -> z",
          "    1:6:android.MediaBrowserCompat$MediaItem[] y(int):1:1 -> w");
  private static final String mappingResult =
      StringUtils.unixLines(
          "# {'id':'com.android.tools.r8.mapping','version':'2.2'}",
          "com.foo -> b:",
          "    java.lang.Object[] newArray(int):58 -> y",
          "    1:6:android.MediaBrowserCompat$MediaItem[] newArray(int):42:42 -> w",
          "    1:2:void m1():13:13 -> z");

  @Test
  public void testCompose() throws Exception {
    ClassNameMapper mappingForFoo =
        ClassNameMapper.mapperFromString(
            mappingFoo, new DiagnosticsHandler() {}, true, false, true);
    ClassNameMapper mappingForBar = ClassNameMapper.mapperFromString(mappingBar);
    String composed = MappingComposer.compose(mappingForFoo, mappingForBar);
    assertEquals(mappingResult, doubleToSingleQuote(composed));
  }
}
