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
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ComposeSyntheticTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  private static final String mappingFoo =
      StringUtils.unixLines(
          "# { id: 'com.android.tools.r8.mapping', version: '2.2' }",
          "com.foo -> a:",
          "# { id: 'com.android.tools.r8.synthesized' }",
          "    int f -> a",
          "    # { id: 'com.android.tools.r8.synthesized' }",
          "    void m() -> b",
          "    # { id: 'com.android.tools.r8.synthesized' }");
  private static final String mappingBar =
      StringUtils.unixLines(
          "# { id: 'com.android.tools.r8.mapping', version: '2.2' }",
          "a -> b:",
          "    int a -> b",
          "com.bar -> c:",
          "# { id: 'com.android.tools.r8.synthesized' }",
          "    void bar() -> a",
          "    # { id: 'com.android.tools.r8.synthesized' }");
  private static final String mappingResult =
      StringUtils.unixLines(
          "# {'id':'com.android.tools.r8.mapping','version':'2.2'}",
          "com.bar -> c:",
          "# {'id':'com.android.tools.r8.synthesized'}",
          "    void bar() -> a",
          "    # {'id':'com.android.tools.r8.synthesized'}",
          "com.foo -> b:",
          "# {'id':'com.android.tools.r8.synthesized'}",
          "    int f -> b",
          "    void m() -> b",
          "    # {'id':'com.android.tools.r8.synthesized'}");

  @Test
  public void testCompose() throws Exception {
    ClassNameMapper mappingForFoo = ClassNameMapper.mapperFromStringWithPreamble(mappingFoo);
    ClassNameMapper mappingForBar = ClassNameMapper.mapperFromStringWithPreamble(mappingBar);
    String composed = MappingComposer.compose(mappingForFoo, mappingForBar);
    assertEquals(mappingResult, doubleToSingleQuote(composed));
  }
}
