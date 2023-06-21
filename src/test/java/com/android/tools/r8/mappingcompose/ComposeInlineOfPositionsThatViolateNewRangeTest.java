// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
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

@RunWith(Parameterized.class)
public class ComposeInlineOfPositionsThatViolateNewRangeTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public ComposeInlineOfPositionsThatViolateNewRangeTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  private static final String mappingFoo =
      StringUtils.unixLines(
          "# {'id':'com.android.tools.r8.mapping','version':'2.2'}",
          "com.foo -> a:",
          "    1:1:void m1():10 -> x",
          "    2:2:void m2(int):20 -> x",
          "    3:4:void y():30:31 -> y");
  private static final String mappingBar =
      StringUtils.unixLines(
          "# {'id':'com.android.tools.r8.mapping','version':'2.2'}",
          "a -> b:",
          "    3:3:void x():1:1 -> z",
          "    3:3:void y():3 -> z",
          "    4:4:void x(int):2:2 -> z",
          "    4:4:void y():4 -> z");
  private static final String mappingBaz =
      StringUtils.unixLines(
          "# {'id':'com.android.tools.r8.mapping','version':'2.2'}",
          "b -> c:",
          "    10:11:void z():3:4 -> w",
          "    10:11:void new_synthetic_method():0 -> w");
  private static final String mappingResult =
      StringUtils.unixLines(
          "# {'id':'com.android.tools.r8.mapping','version':'2.2'}",
          "com.foo -> c:",
          "    10:10:void m1():10 -> w",
          "    10:10:void y():30 -> w",
          "    10:10:void new_synthetic_method():0:0 -> w",
          "    11:11:void m2(int):20 -> w",
          "    11:11:void y():31 -> w",
          "    11:11:void new_synthetic_method():0:0 -> w");

  @Test
  public void testCompose() throws Exception {
    ClassNameMapper mappingForFoo = ClassNameMapper.mapperFromString(mappingFoo);
    ClassNameMapper mappingForBar = ClassNameMapper.mapperFromString(mappingBar);
    ClassNameMapper mappingForBaz = ClassNameMapper.mapperFromString(mappingBaz);
    String composed = MappingComposer.compose(mappingForFoo, mappingForBar, mappingForBaz);
    assertEquals(mappingResult, doubleToSingleQuote(composed));
  }
}
