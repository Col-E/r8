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
public class ComposeInlineFollowedByInlineTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  private static final String mappingFoo =
      StringUtils.unixLines(
          "# {'id':'com.android.tools.r8.mapping','version':'experimental'}",
          "com.foo -> a:",
          "    1:3:void inlinee1():42:44 -> x",
          "    1:3:void foo.bar.baz.inlinee1():41 -> x",
          "    1:3:void caller():40 -> x",
          "    1:3:void inlinee2():48:50 -> y",
          "    1:3:void caller2():27 -> y",
          "com.bar -> b:",
          "    1:3:void inlinee2():42:44 -> x",
          "    1:3:void foo.bar.baz.inlinee1():41 -> x",
          "    1:3:void caller():40 -> x");
  private static final String mappingBar =
      StringUtils.unixLines(
          "# {'id':'com.android.tools.r8.mapping','version':'experimental'}",
          "a -> b:",
          "    1:1:void x():3:3 -> z",
          "    1:1:void qux():1 -> z",
          "    4:5:void x():2:3 -> z",
          "    4:5:void y():2 -> z",
          "b -> c:",
          "    2:2:void a.x():2:2 -> y",
          "    2:2:void x():1 -> y",
          "    2:2:void qux():1 -> y");
  private static final String mappingResult =
      StringUtils.unixLines(
          "# {'id':'com.android.tools.r8.mapping','version':'experimental'}",
          "com.bar -> c:",
          "    2:2:void com.foo.inlinee1():43:43 -> y",
          "    2:2:void foo.bar.baz.inlinee1():41 -> y",
          "    2:2:void com.foo.caller():40 -> y",
          "    2:2:void inlinee2():42:42 -> y",
          "    2:2:void foo.bar.baz.inlinee1():41 -> y",
          "    2:2:void caller():40 -> y",
          "    2:2:void qux():1 -> y",
          "com.foo -> b:",
          "    1:1:void inlinee1():44:44 -> z",
          "    1:1:void foo.bar.baz.inlinee1():41 -> z",
          "    1:1:void caller():40 -> z",
          "    1:1:void qux():1 -> z",
          "    4:5:void inlinee1():43:44 -> z",
          "    4:5:void foo.bar.baz.inlinee1():41 -> z",
          "    4:5:void caller():40 -> z",
          "    4:5:void inlinee2():49:49 -> z",
          "    4:5:void caller2():27 -> z");

  @Test
  public void testCompose() throws Exception {
    ClassNameMapper mappingForFoo = ClassNameMapper.mapperFromString(mappingFoo);
    ClassNameMapper mappingForBar = ClassNameMapper.mapperFromString(mappingBar);
    String composed = MappingComposer.compose(mappingForFoo, mappingForBar);
    assertEquals(mappingResult, doubleToSingleQuote(composed));
  }
}
