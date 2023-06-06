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

public class ComposeOutlineWithIdRangeTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  private static final String mappingFoo =
      StringUtils.unixLines(
          "# {'id':'com.android.tools.r8.mapping','version':'2.2'}",
          "package.Class$$ExternalSyntheticOutline0 -> package.internal.X:",
          "# {'id':'sourceFile','fileName':'R8$$SyntheticClass'}",
          "# {'id':'com.android.tools.r8.synthesized'}",
          "    1:3:long package.Int2IntLinkedOpenHashMap$$InternalSyntheticOutline$HASH$0"
              + ".m(long,long,long):0:2"
              + " -> a",
          "      # {'id':'com.android.tools.r8.synthesized'}",
          "      # {'id':'com.android.tools.r8.outline'}",
          "package.Class -> package.internal.Y:",
          "# {'id':'sourceFile','fileName':'FieldDefinition.java'}",
          "    1:6:void foo():21:26 -> a",
          "    7:7:void foo():0:0 -> a",
          "    # {'id':'com.android.tools.r8.outlineCallsite',"
              + "'positions':{'1':10,'2':11},"
              + "'outline':'Lpackage/internal/X;a(JJJ)J'}",
          "    8:9:void foo():38:39 -> a",
          "    10:10:void inlineeInOutline():1337:1337 -> a",
          "    10:10:void foo():42 -> a",
          "    11:11:void foo():44:44 -> a");
  private static final String mappingBar =
      StringUtils.unixLines(
          "# {'id':'com.android.tools.r8.mapping','version':'2.2'}",
          "package.internal.X -> package.new_internal.X:",
          "    1:3:long a(long,long,long) -> b",
          "package.internal.Y -> package.new_internal.Y:",
          "    1:8:void a() -> b");
  private static final String mappingResult =
      StringUtils.unixLines(
          "# {'id':'com.android.tools.r8.mapping','version':'2.2'}",
          "package.Class -> package.new_internal.Y:",
          "# {'id':'sourceFile','fileName':'FieldDefinition.java'}",
          "    1:6:void foo():21:26 -> b",
          "    7:7:void foo():0:0 -> b",
          "    # {'id':'com.android.tools.r8.outlineCallsite',"
              + "'positions':{'1':9,'2':10},"
              + "'outline':'Lpackage/new_internal/X;b(JJJ)J'}",
          "    8:8:void foo():38:38 -> b",
          "    9:9:void inlineeInOutline():1337:1337 -> a",
          "    9:9:void foo():42 -> b",
          "    10:10:void foo():44:44 -> b",
          "package.Class$$ExternalSyntheticOutline0 -> package.new_internal.X:",
          "# {'id':'sourceFile','fileName':'R8$$SyntheticClass'}",
          "# {'id':'com.android.tools.r8.synthesized'}",
          "    1:3:long package.Int2IntLinkedOpenHashMap$$InternalSyntheticOutline$HASH$0"
              + ".m(long,long,long):0:2 -> b",
          "    # {'id':'com.android.tools.r8.synthesized'}",
          "    # {'id':'com.android.tools.r8.outline'}");

  @Test
  public void testCompose() throws Exception {
    ClassNameMapper mappingForFoo = ClassNameMapper.mapperFromString(mappingFoo);
    ClassNameMapper mappingForBar = ClassNameMapper.mapperFromString(mappingBar);
    String composed = MappingComposer.compose(mappingForFoo, mappingForBar);
    assertEquals(mappingResult, doubleToSingleQuote(composed));
  }
}
