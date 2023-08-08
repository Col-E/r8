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
public class ComposeInlineRangeOverlappingTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  private static final String mappingFoo =
      StringUtils.unixLines(
          "# {'id':'com.android.tools.r8.mapping','version':'2.2'}",
          "com.android.ViewOptions -> com.android.ViewOptions:",
          "    46:47:com.android.InternalViewOptions "
              + "com.android.InternalViewOptions.parseFromJson(org.json.JSONObject):44:45 -> e",
          "    46:47:com.android.ViewOptions parseFromJson(org.json.JSONObject):97 -> e",
          "    48:49:com.android.InternalViewOptions "
              + "com.android.InternalViewOptions.parseFromJson(org.json.JSONObject):47:48 -> e",
          "    48:49:com.android.ViewOptions parseFromJson(org.json.JSONObject):97 -> e");
  private static final String mappingBar =
      StringUtils.unixLines(
          "# {'id':'com.android.tools.r8.mapping','version':'2.2'}",
          "com.android.ViewOptions -> com.android.ViewOptions:",
          "    147:148:com.android.ViewOptions e(org.json.JSONObject):47:48 -> e");
  private static final String mappingResult =
      StringUtils.unixLines(
          "# {'id':'com.android.tools.r8.mapping','version':'2.2'}",
          "com.android.ViewOptions -> com.android.ViewOptions:",
          "    147:147:com.android.InternalViewOptions "
              + "com.android.InternalViewOptions.parseFromJson(org.json.JSONObject):45:45 -> e",
          "    147:147:com.android.ViewOptions parseFromJson(org.json.JSONObject):97 -> e",
          // TODO(b/280564959): We should not introduce ambiguous mapping.
          "    148:148:com.android.InternalViewOptions "
              + "com.android.InternalViewOptions.parseFromJson(org.json.JSONObject):47:48 -> e",
          "    148:148:com.android.ViewOptions parseFromJson(org.json.JSONObject):97 -> e");

  @Test
  public void testCompose() throws Exception {
    ClassNameMapper mappingForFoo = ClassNameMapper.mapperFromStringWithPreamble(mappingFoo);
    ClassNameMapper mappingForBar = ClassNameMapper.mapperFromStringWithPreamble(mappingBar);
    String composed = MappingComposer.compose(mappingForFoo, mappingForBar);
    assertEquals(mappingResult, doubleToSingleQuote(composed));
  }
}
