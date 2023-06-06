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

public class ComposeOutlineInlinedIntoOutlineAndInlinedTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  private static final String mappingFoo =
      StringUtils.unixLines(
          "# { id: 'com.android.tools.r8.mapping', version: '2.2' }",
          "Outline1 -> A:",
          "    1:2:int outline1():0:1 -> a",
          "    # { 'id':'com.android.tools.r8.outline' }",
          "Outline2 -> B:",
          "    1:2:int outline2():0:1 -> a",
          "    # { 'id':'com.android.tools.r8.outline' }",
          "outline.Callsite1 -> X:",
          "    1:2:int caller1(int):41:42 -> s",
          "    3:3:int caller1(int):0:0 -> s",
          "    # { 'id':'com.android.tools.r8.outlineCallsite',"
              + "'positions': { '1': 9, '2': 10 },"
              + "'outline':'La;a()I' }",
          "    4:6:int caller1(int):48:49 -> s",
          "    9:9:int caller1(int):23 -> s",
          "    10:10:int caller1(int):24 -> s",
          "outline.Callsite2 -> Y:",
          "    1:1:int caller2(int):0:0 -> s",
          "    # { 'id':'com.android.tools.r8.outlineCallsite',"
              + "'positions': { '1': 2, '2': 3 },"
              + "'outline':'La;a()I' }",
          "    2:2:int caller2(int):23:23 -> s",
          "    3:3:int caller2(int):24:24 -> s");
  private static final String mappingBar =
      StringUtils.unixLines(
          "# {'id':'com.android.tools.r8.mapping','version':'2.2'}",
          "Y -> Z:",
          "    1:2:int A.a():1:2 -> a",
          "    1:2:int X.s(int):3 -> a",
          "    1:2:int B.a():1 -> a",
          "    1:2:int s(int):1 -> a",
          "    3:3:int B.a():2:2 -> a",
          "    3:3:int s(int):1 -> a");
  private static final String mappingResult =
      StringUtils.unixLines(
          "# {'id':'com.android.tools.r8.mapping','version':'2.2'}",
          "Outline1 -> A:",
          "Outline2 -> B:",
          "outline.Callsite1 -> X:",
          "outline.Callsite2 -> Z:",
          "    1:1:int outline.Callsite1.caller1(int):23 -> a",
          "    1:1:int caller2(int):23:23 -> a",
          "    2:2:int outline.Callsite1.caller1(int):24 -> a",
          "    2:2:int caller2(int):23:23 -> a",
          "    3:3:int caller2(int):24:24 -> a");

  @Test
  public void testCompose() throws Exception {
    ClassNameMapper mappingForFoo = ClassNameMapper.mapperFromString(mappingFoo);
    ClassNameMapper mappingForBar = ClassNameMapper.mapperFromString(mappingBar);
    String composed = MappingComposer.compose(mappingForFoo, mappingForBar);
    assertEquals(mappingResult, doubleToSingleQuote(composed));
  }
}
