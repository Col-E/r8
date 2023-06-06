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
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

public class ComposeOutlineHavingInlineInlinedTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  private static final String mappingFoo =
      StringUtils.unixLines(
          "# { id: 'com.android.tools.r8.mapping', version: '2.2' }",
          "outline.Class -> A:",
          "    1:2:int some.inlinee():75:76 -> a",
          "    1:2:int outline():0 -> a",
          "    # { 'id':'com.android.tools.r8.outline' }",
          "outline.Callsite -> X:",
          "    1:2:int outlineCaller(int):41:42 -> s",
          "    3:3:int outlineCaller(int):0:0 -> s",
          "    # { 'id':'com.android.tools.r8.outlineCallsite',"
              + "'positions': { '1': 9, '2': 10 },"
              + "'outline':'La;a()I' }",
          "    9:9:int outlineCaller(int):23 -> s",
          "    10:10:int foo.bar.baz.outlineCaller(int):98:98 -> s",
          "    10:10:int outlineCaller(int):24 -> s");
  private static final String mappingBar =
      StringUtils.unixLines(
          "# {'id':'com.android.tools.r8.mapping','version':'2.2'}",
          "X -> Y:",
          "    1:1:int A.a():1:1 -> s",
          "    1:1:int s(int):3 -> s",
          "    2:4:int another.inline():102:104 -> s",
          "    2:4:int A.a():2 -> s",
          "    2:4:int s(int):3 -> s");
  private static final String mappingResult =
      StringUtils.unixLines(
          "# {'id':'com.android.tools.r8.mapping','version':'2.2'}",
          "outline.Callsite -> Y:",
          "    1:1:int some.inlinee():75:75 -> s",
          "    1:1:int outlineCaller(int):23 -> s",
          "    2:4:int another.inline():102:104 -> s",
          "    2:4:int some.inlinee():76:76 -> s",
          "    2:4:int foo.bar.baz.outlineCaller(int):98:98 -> s",
          "    2:4:int outlineCaller(int):24 -> s",
          "outline.Class -> A:");

  @Test
  public void testCompose() throws Exception {
    ClassNameMapper mappingForFoo = ClassNameMapper.mapperFromString(mappingFoo);
    ClassNameMapper mappingForBar = ClassNameMapper.mapperFromString(mappingBar);
    String composed = MappingComposer.compose(mappingForFoo, mappingForBar);
    assertEquals(mappingResult, doubleToSingleQuote(composed));
  }
}
