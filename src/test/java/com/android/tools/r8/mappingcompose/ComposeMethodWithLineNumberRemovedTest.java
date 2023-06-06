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

/*
 * This is a regression test for b/284925475.
 */
@RunWith(Parameterized.class)
public class ComposeMethodWithLineNumberRemovedTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  private static final String mappingFoo =
      StringUtils.unixLines(
          "# {'id':'com.android.tools.r8.mapping','version':'2.2'}",
          "package.FieldDefinition$FullFieldDefinition -> package.internal.Th:",
          "package.FieldDefinition -> package.internal.Uh:",
          "# {'id':'sourceFile','fileName':'FieldDefinition.java'}",
          "    1:1:void <init>():13:13 -> <init>",
          "    1:1:package.FieldDefinition$FullFieldDefinition asFullFieldDefinition():0:0 -> a",
          "    # {'id':'com.android.tools.r8.residualsignature',"
              + "'signature':'()Lpackage/internal/Th;'}");
  private static final String mappingBar =
      StringUtils.unixLines(
          "# {'id':'com.android.tools.r8.mapping','version':'2.2'}",
          "package.ClassReference ->" + " package.ClassReference:",
          "package.internal.Th -> package.other_internal.F1:",
          "package.internal.Uh -> package.other_internal.M1:",
          "# {'id':'sourceFile','fileName':'R8_new_hash_name'}",
          "    1:1:void <init>() -> <init>",
          "    package.internal.Th a() -> b",
          "    # {'id':'com.android.tools.r8.residualsignature',"
              + "'signature':'()Lpackage/retrace_internal/F1;'}");
  private static final String mappingResult =
      StringUtils.unixLines(
          "# {'id':'com.android.tools.r8.mapping','version':'2.2'}",
          "package.ClassReference -> package.ClassReference:",
          "package.FieldDefinition -> package.other_internal.M1:",
          "# {'id':'sourceFile','fileName':'FieldDefinition.java'}",
          "    package.internal.Th a() -> b",
          "    # {'id':'com.android.tools.r8.residualsignature',"
              + "'signature':'()Lpackage/retrace_internal/F1;'}",
          "    1:1:void <init>():13:13 -> <init>",
          "package.FieldDefinition$FullFieldDefinition -> package.other_internal.F1:");

  @Test
  public void testCompose() throws Exception {
    ClassNameMapper mappingForFoo = ClassNameMapper.mapperFromString(mappingFoo);
    ClassNameMapper mappingForBar = ClassNameMapper.mapperFromString(mappingBar);
    String composed = MappingComposer.compose(mappingForFoo, mappingForBar);
    assertEquals(mappingResult, doubleToSingleQuote(composed));
  }
}
