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
import org.junit.runners.Parameterized.Parameters;

/** This is a regression test for b/288117378. */
@RunWith(Parameterized.class)
public class ComposeOutlineCallSiteInlinedTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public ComposeOutlineCallSiteInlinedTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  private static final String mappingFoo =
      StringUtils.unixLines(
          "# {'id':'com.android.tools.r8.mapping','version':'2.2'}",
          "com.android.tools.r8.D8Command -> com.android.tools.r8.D8Command:",
          "# {'id':'sourceFile','fileName':'D8Command.java'}",
          "    1:1:java.util.List getParseFlagsInformation():592:592 -> getParseFlagsInformation",
          "    1:1:foo.MapConsumer lambda$bar$0(foo.StringConsumer):0:0 -> lambda$bar$0",
          "      # {'id':'com.android.tools.r8.residualsignature',"
              + "'signature':'(Lfoo/StringConsumer;)Lfoo/internal/MapConsumer;'}",
          "      # {'id':'com.android.tools.r8.outlineCallsite',"
              + "'positions':{'23':724,'24':725,'25':726},"
              + "'outline':'Lfoo/SomeClass;outline(JJJ)V'}",
          "    724:724:foo.MapConsumer lambda$bar$0(foo.StringConsumer):720:720 -> lambda$bar$0",
          "    725:725:foo.PGMapConsumer foo.PGMapConsumer.builder():52:52 -> lambda$bar$0",
          "    725:725:foo.MapConsumer lambda$bar$0(foo.StringConsumer):720 -> lambda$bar$0",
          "    726:726:void foo.PGMapConsumer.<init>():55:55 -> lambda$bar$0",
          "    726:726:foo.PGMapConsumer foo.PGMapConsumer.builder():52 -> lambda$bar$0",
          "    726:726:foo.MapConsumer lambda$bar$0(foo.StringConsumer):720 -> lambda$bar$0");
  private static final String mappingBar =
      StringUtils.unixLines(
          "# {'id':'com.android.tools.r8.mapping','version':'2.2'}",
          "com.android.tools.r8.D8Command -> com.android.tools.r8.D8Command:",
          "# {'id':'sourceFile','fileName':'SourceFile'}",
          "    1:724:foo.internal.MapConsumer lambda$bar$0(foo.StringConsumer):0:723"
              + " -> lambda$bar$0$new",
          "    1:724:foo.internal.MapConsumer"
              + " lambda$bar$0$new(foo.StringConsumer):0"
              + " -> lambda$bar$0$new",
          "      # {'id':'com.android.tools.r8.synthesized'}");
  private static final String mappingResult =
      StringUtils.unixLines(
          "# {'id':'com.android.tools.r8.mapping','version':'2.2'}",
          "com.android.tools.r8.D8Command -> com.android.tools.r8.D8Command:",
          "# {'id':'sourceFile','fileName':'D8Command.java'}",
          "    1:1:java.util.List getParseFlagsInformation():592:592 -> getParseFlagsInformation",
          // The following two frames is the preamble it is mapping to position 0. There is no
          // positional information in mappingFoo, so the right thing here is to use the original
          // signature from mappingBar/residual signature from mappingFoo.
          "    1:1:foo.internal.MapConsumer lambda$bar$0(foo.StringConsumer):0:0 ->"
              + " lambda$bar$0$new",
          "    1:1:foo.internal.MapConsumer"
              + " lambda$bar$0$new(foo.StringConsumer):0:0 -> lambda$bar$0$new",
          "    # {'id':'com.android.tools.r8.synthesized'}",
          // This frame is the outline position in mappingFoo.
          "    2:2:foo.MapConsumer lambda$bar$0(foo.StringConsumer):0:0 -> lambda$bar$0$new",
          "    # {'id':'com.android.tools.r8.residualsignature',"
              + "'signature':'(Lfoo/StringConsumer;)Lfoo/internal/MapConsumer;'}",
          "    # {'id':'com.android.tools.r8.outlineCallsite',"
              + "'positions':{'23':725,'24':726,'25':727},"
              + "'outline':'Lfoo/SomeClass;outline(JJJ)V'}",
          "    2:2:foo.internal.MapConsumer"
              + " lambda$bar$0$new(foo.StringConsumer):0:0 -> lambda$bar$0$new",
          // This is the tail before any synthetic outline positions where the only information
          // is in mappingBar.
          "    3:724:foo.internal.MapConsumer lambda$bar$0(foo.StringConsumer) -> lambda$bar$0$new",
          "    3:724:foo.internal.MapConsumer lambda$bar$0$new(foo.StringConsumer):0:0 "
              + "-> lambda$bar$0$new",
          // The remaining positions are the synthetic outline positions needed for correct
          // retracing.
          "    725:725:foo.MapConsumer lambda$bar$0(foo.StringConsumer):720:720 "
              + "-> lambda$bar$0$new",
          "    726:726:foo.PGMapConsumer foo.PGMapConsumer.builder():52:52 -> lambda$bar$0$new",
          "    726:726:foo.MapConsumer lambda$bar$0(foo.StringConsumer):720 -> lambda$bar$0$new",
          "    727:727:void foo.PGMapConsumer.<init>():55:55 -> lambda$bar$0$new",
          "    727:727:foo.PGMapConsumer foo.PGMapConsumer.builder():52 -> lambda$bar$0$new",
          "    727:727:foo.MapConsumer lambda$bar$0(foo.StringConsumer):720 -> lambda$bar$0$new");

  @Test
  public void testCompose() throws Exception {
    ClassNameMapper mappingForFoo = ClassNameMapper.mapperFromString(mappingFoo);
    ClassNameMapper mappingForBar = ClassNameMapper.mapperFromString(mappingBar);
    String composed = MappingComposer.compose(mappingForFoo, mappingForBar);
    assertEquals(mappingResult, doubleToSingleQuote(composed));
  }
}
