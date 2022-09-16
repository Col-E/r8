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
public class ComposeNoResidualSignatureExistingNamingLineNumberTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  private static final String mappingFoo =
      StringUtils.unixLines(
          "# {'id':'com.android.tools.r8.mapping','version':'experimental'}",
          "com.Class1 -> A:",
          "    1:1:com.Class1 m(com.Class2[][]):42:42 -> a",
          "com.Class2 -> B:");
  private static final String mappingBar =
      StringUtils.unixLines(
          "# {'id':'com.android.tools.r8.mapping','version':'experimental'}",
          "A -> B:",
          "    2:2:A a(B[][]):1:1 -> b",
          "B -> C:");
  private static final String mappingBaz =
      StringUtils.unixLines(
          "# {'id':'com.android.tools.r8.mapping','version':'experimental'}",
          "B -> C:",
          "    3:3:B b(C[][]):2:2 -> c",
          "C -> D:");
  private static final String mappingResult =
      StringUtils.unixLines(
          "# {'id':'com.android.tools.r8.mapping','version':'experimental'}",
          "com.Class1 -> C:",
          "    3:3:com.Class1 m(com.Class2[][]):42:42 -> c",
          "com.Class2 -> D:");

  @Test
  public void testCompose() throws Exception {
    ClassNameMapper mappingForFoo = ClassNameMapper.mapperFromStringWithExperimental(mappingFoo);
    ClassNameMapper mappingForBar = ClassNameMapper.mapperFromStringWithExperimental(mappingBar);
    ClassNameMapper mappingForBaz = ClassNameMapper.mapperFromStringWithExperimental(mappingBaz);
    String composed = MappingComposer.compose(mappingForFoo, mappingForBar, mappingForBaz);
    assertEquals(mappingResult, doubleToSingleQuote(composed));
  }
}
