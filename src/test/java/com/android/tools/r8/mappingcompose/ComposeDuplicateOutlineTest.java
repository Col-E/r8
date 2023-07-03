// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.mappingcompose;

import static org.junit.Assert.assertThrows;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.naming.MappingComposeException;
import com.android.tools.r8.naming.MappingComposer;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/* This is a regression test for b/289788048 */
public class ComposeDuplicateOutlineTest extends TestBase {

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
          "    1:2:long package.Int2IntLinkedOpenHashMap$$InternalSyntheticOutline$HASH$0"
              + ".m(long,long,long):0:1"
              + " -> a",
          "      # {'id':'com.android.tools.r8.synthesized'}",
          "      # {'id':'com.android.tools.r8.outline'}",
          "package.Class -> package.internal.Y:",
          "# {'id':'sourceFile','fileName':'FieldDefinition.java'}",
          "    1:1:void foo():21:21 -> a",
          "    # {'id':'com.android.tools.r8.outlineCallsite',"
              + "'positions':{'1':2,'2':3},"
              + "'outline':'Lpackage/internal/X;a(JJJ)J'}",
          "    2:2:void foo():1337:1337 -> a",
          "    3:3:void foo():44:44 -> a");
  private static final String mappingBar =
      StringUtils.unixLines(
          "# {'id':'com.android.tools.r8.mapping','version':'2.2'}",
          "package.internal.Y -> package.new_internal.Y:",
          // If there are multiple throwing instructions for a line number, we can get multiple
          // mappings in D8.
          "    1:10:void a():1:1 -> b",
          "    11:20:void a():1:1 -> b");

  @Test
  public void testCompose() throws Exception {
    ClassNameMapper mappingForFoo = ClassNameMapper.mapperFromString(mappingFoo);
    ClassNameMapper mappingForBar = ClassNameMapper.mapperFromString(mappingBar);
    // TODO(b/289788048): We should be able to handle less precise input.
    assertThrows(
        MappingComposeException.class, () -> MappingComposer.compose(mappingForFoo, mappingForBar));
  }
}
