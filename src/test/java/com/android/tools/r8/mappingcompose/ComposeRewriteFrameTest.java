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
public class ComposeRewriteFrameTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  private static final String mappingFoo =
      StringUtils.unixLines(
          "# { id: 'com.android.tools.r8.mapping', version: '2.1' }",
          "my.CustomException -> a:",
          "foo.Bar -> x:",
          "    4:4:void other.Class.inlinee():23:23 -> a",
          "    4:4:void caller(other.Class):7 -> a",
          "    # { id: 'com.android.tools.r8.rewriteFrame', "
              + "conditions: ['throws(La;)'], actions: ['removeInnerFrames(1)'] }");
  private static final String mappingBar =
      StringUtils.unixLines(
          "# { id: 'com.android.tools.r8.mapping', version: '2.1' }",
          "a -> b:",
          "x -> c:",
          "    8:8:void a(Other.Class):4:4 -> m");
  private static final String mappingResult =
      StringUtils.unixLines(
          "# {'id':'com.android.tools.r8.mapping','version':'2.1'}",
          "foo.Bar -> c:",
          "    8:8:void other.Class.inlinee():23:23 -> m",
          "    8:8:void caller(other.Class):7 -> m",
          "    # {'id':'com.android.tools.r8.rewriteFrame','conditions':['throws(Lb;)'],"
              + "'actions':['removeInnerFrames(1)']}",
          "my.CustomException -> b:");

  @Test
  public void testCompose() throws Exception {
    ClassNameMapper mappingForFoo = ClassNameMapper.mapperFromString(mappingFoo);
    ClassNameMapper mappingForBar = ClassNameMapper.mapperFromString(mappingBar);
    String composed = MappingComposer.compose(mappingForFoo, mappingForBar);
    assertEquals(mappingResult, doubleToSingleQuote(composed));
  }
}
