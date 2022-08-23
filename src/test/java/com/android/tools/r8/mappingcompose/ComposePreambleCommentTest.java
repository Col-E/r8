// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.mappingcompose;

import static com.android.tools.r8.mappingcompose.ComposeHelpers.doubleToSingleQuote;
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
public class ComposePreambleCommentTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  private static final String mappingFoo =
      StringUtils.unixLines(
          "# This is a multi line ",
          "# preamble, with custom information",
          "# {'id':'com.android.tools.r8.mapping','version':'2.1'}",
          "# foo bar",
          "# {'id':'this is invalid json due to no comma' neededInfo:'foobar' }",
          "com.A -> a:",
          "# This is a comment that will be removed.",
          "com.B -> c:");
  private static final String mappingBar =
      StringUtils.unixLines(
          "# Additional multiline ",
          "# second preamble, with custom information",
          "# {'id':'bar',neededInfo:'barbaz'}",
          "# {'id':'com.android.tools.r8.mapping','version':'2.1'}",
          "a -> b:",
          "# This is another comment that will be removed.",
          "c -> d:");
  private static final String mappingResult =
      StringUtils.unixLines(
          "# This is a multi line ",
          "# preamble, with custom information",
          "# foo bar",
          "# {'id':'this is invalid json due to no comma' neededInfo:'foobar' }",
          "# Additional multiline ",
          "# second preamble, with custom information",
          "# {'id':'bar',neededInfo:'barbaz'}",
          "# {'id':'com.android.tools.r8.mapping','version':'2.1'}",
          "com.A -> b:",
          "com.B -> d:");

  @Test
  public void testCompose() throws Exception {
    ClassNameMapper mappingForFoo = ClassNameMapper.mapperFromStringWithPreamble(mappingFoo);
    ClassNameMapper mappingForBar = ClassNameMapper.mapperFromStringWithPreamble(mappingBar);
    String composed = MappingComposer.compose(mappingForFoo, mappingForBar);
    assertEquals(mappingResult, doubleToSingleQuote(composed));
  }
}
