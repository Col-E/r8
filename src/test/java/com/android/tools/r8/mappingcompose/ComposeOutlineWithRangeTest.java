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
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** This is a regression test for b/286781273. */
@RunWith(Parameterized.class)
public class ComposeOutlineWithRangeTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  private static final String mappingFoo =
      StringUtils.unixLines(
          "# { id: 'com.android.tools.r8.mapping', version: '2.2' }",
          "outline.Class -> a:",
          "    1:2:int some.inlinee():75:76 -> a",
          "    1:2:int outline():0 -> a",
          "    # { 'id':'com.android.tools.r8.outline' }",
          "outline.Callsite -> x:",
          "    1:1:int outlineCaller(int):0:0 -> s",
          "    # { 'id':'com.android.tools.r8.outlineCallsite',"
              + "'positions': { '1': 10, '2': 11 },"
              + "'outline':'La;a()I' }",
          "    10:11:int outlineCaller(int):23:24 -> s");
  private static final String mappingBar =
      StringUtils.unixLines(
          "# {'id':'com.android.tools.r8.mapping','version':'2.2'}",
          "a -> b:",
          "    4:5:int a():1:2 -> m",
          "x -> y:",
          "    42:42:int s(int):1:1 -> o");
  private static final String mappingResult =
      StringUtils.unixLines(
          "# {'id':'com.android.tools.r8.mapping','version':'2.2'}",
          "outline.Callsite -> y:",
          "    42:42:int outlineCaller(int):0:0 -> o",
          "    # {'id':'com.android.tools.r8.outlineCallsite',"
              + "'positions':{'4':43,'5':44},"
              + "'outline':'Lb;m()I'}",
          "    43:43:int outlineCaller(int):23:23 -> o",
          "    44:44:int outlineCaller(int):24:24 -> o",
          "outline.Class -> b:",
          "    4:5:int some.inlinee():75:76 -> m",
          "    4:5:int outline():0 -> m",
          "    # {'id':'com.android.tools.r8.outline'}");

  @Test
  public void testCompose() throws Exception {
    ClassNameMapper mappingForFoo = ClassNameMapper.mapperFromStringWithPreamble(mappingFoo);
    ClassNameMapper mappingForBar = ClassNameMapper.mapperFromStringWithPreamble(mappingBar);
    String composed = MappingComposer.compose(mappingForFoo, mappingForBar);
    assertEquals(mappingResult, doubleToSingleQuote(composed));
  }
}
