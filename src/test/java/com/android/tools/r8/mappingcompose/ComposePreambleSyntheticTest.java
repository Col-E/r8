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
public class ComposePreambleSyntheticTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public ComposePreambleSyntheticTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  private static final String mappingFoo =
      StringUtils.unixLines(
          "# {'id':'com.android.tools.r8.mapping','version':'2.2'}",
          "com.foo -> a:",
          "    1:1:void lambda$0(boolean):355:355 ->" + " lambda$0");
  private static final String mappingBar =
      StringUtils.unixLines(
          "# {'id':'com.android.tools.r8.mapping','version':'2.2'}",
          "a -> b:",
          "    1:2:void lambda$0(boolean):0:1 ->" + " lambda$0$com-r8-Base",
          "    1:2:void lambda$0$com-r8-Base(boolean):0 -> lambda$0$com-r8-Base",
          "    # {'id':'com.android.tools.r8.synthesized'}");
  private static final String mappingResult =
      StringUtils.unixLines(
          "# {'id':'com.android.tools.r8.mapping','version':'2.2'}",
          "com.foo -> b:",
          "    1:1:void lambda$0(boolean):0:0 -> lambda$0$com-r8-Base",
          "    1:1:void lambda$0$com-r8-Base(boolean):0:0 -> lambda$0$com-r8-Base",
          "    # {'id':'com.android.tools.r8.synthesized'}",
          "    2:2:void lambda$0(boolean):355:355 -> lambda$0$com-r8-Base",
          "    2:2:void lambda$0$com-r8-Base(boolean):0:0 -> lambda$0$com-r8-Base");

  @Test
  public void testCompose() throws Exception {
    ClassNameMapper mappingForFoo = ClassNameMapper.mapperFromString(mappingFoo);
    ClassNameMapper mappingForBar = ClassNameMapper.mapperFromString(mappingBar);
    String composed = MappingComposer.compose(mappingForFoo, mappingForBar);
    assertEquals(mappingResult, doubleToSingleQuote(composed));
  }
}
