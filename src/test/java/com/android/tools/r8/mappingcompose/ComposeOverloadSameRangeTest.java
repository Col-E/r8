// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.mappingcompose;

import static com.android.tools.r8.mappingcompose.ComposeTestHelpers.doubleToSingleQuote;
import static org.junit.Assert.assertEquals;

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

/** This is a regression test for b/286781273. */
@RunWith(Parameterized.class)
public class ComposeOverloadSameRangeTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public ComposeOverloadSameRangeTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  private static final String mappingFoo =
      StringUtils.unixLines(
          "# {'id':'com.android.tools.r8.mapping','version':'2.2'}",
          "com.foo -> a:",
          "    1:2:void method():41:42 -> m",
          "    3:3:void inlinee():20:20 -> m",
          "    3:3:void method():43 -> m",
          "    1:2:void method(int):111:112 -> m",
          "    3:3:void method(int):113:113 -> m");
  private static final String mappingBar =
      StringUtils.unixLines(
          "# {'id':'com.android.tools.r8.mapping','version':'2.2'}",
          "a -> b:",
          "    1:3:void m():1:3 -> m",
          "    1:3:void m(int):1:3 -> m");
  private static final String mappingResult =
      StringUtils.unixLines(
          "# {'id':'com.android.tools.r8.mapping','version':'2.2'}",
          "com.foo -> b:",
          "    1:2:void method():41:42 -> m",
          "    3:3:void inlinee():20:20 -> m",
          "    3:3:void method():43 -> m",
          "    1:2:void method(int):111:112 -> m",
          "    3:3:void method(int):113:113 -> m");

  @Test
  public void testCompose() throws Exception {
    ClassNameMapper mappingForFoo = ClassNameMapper.mapperFromStringWithPreamble(mappingFoo);
    ClassNameMapper mappingForBar = ClassNameMapper.mapperFromStringWithPreamble(mappingBar);
    String composed = MappingComposer.compose(mappingForFoo, mappingForBar);
    assertEquals(mappingResult, doubleToSingleQuote(composed));
  }
}
