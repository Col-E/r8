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

@RunWith(Parameterized.class)
public class ComposeWithHolesTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  private static final String mappingFoo =
      StringUtils.unixLines(
          "# {'id':'com.android.tools.r8.mapping','version':'2.2'}",
          "com.foo -> A:",
          "    2:11:java.lang.String internalToDescriptor(java.lang.String,boolean):45:54 -> a",
          "    72:72:java.lang.String internalToDescriptor(java.lang.String,boolean):56:56 -> a");
  private static final String mappingBar =
      StringUtils.unixLines(
          "# {'id':'com.android.tools.r8.mapping','version':'2.2'}",
          "A -> B:",
          "    1:71:java.lang.String a(java.lang.String,boolean):2:72 -> e");
  private static final String mappingResult =
      StringUtils.unixLines(
          "# {'id':'com.android.tools.r8.mapping','version':'2.2'}",
          "com.foo -> B:",
          "    1:10:java.lang.String internalToDescriptor(java.lang.String,boolean):45:54 -> e",
          "    11:70:java.lang.String a(java.lang.String,boolean) -> e",
          "    71:71:java.lang.String internalToDescriptor(java.lang.String,boolean):56:56 -> e");

  @Test
  public void testCompose() throws Exception {
    ClassNameMapper mappingForFoo = ClassNameMapper.mapperFromStringWithPreamble(mappingFoo);
    ClassNameMapper mappingForBar = ClassNameMapper.mapperFromStringWithPreamble(mappingBar);
    String composed = MappingComposer.compose(mappingForFoo, mappingForBar);
    assertEquals(mappingResult, doubleToSingleQuote(composed));
  }
}
