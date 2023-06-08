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
public class ComposeSourceFileTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  private static final String mappingFoo =
      StringUtils.unixLines(
          "# {'id':'com.android.tools.r8.mapping','version':'2.2'}",
          "com.foo -> A:",
          "    # {'id':'sourceFile','fileName':'Foo.kt'}",
          "com.bar -> B:",
          "    # {'id':'sourceFile','fileName':'Bar.kt'}",
          "com.baz -> C:");
  private static final String mappingBar =
      StringUtils.unixLines(
          "# {'id':'com.android.tools.r8.mapping','version':'2.2'}",
          "A -> a:",
          "    # {'id':'sourceFile','fileName':'some-hash-inserted-into-source-file'}",
          "B -> b:",
          "C -> c:",
          "    # {'id':'sourceFile','fileName':'some-other-hash-inserted-into-source-file'}",
          "com.qux -> d:",
          "    # {'id':'sourceFile','fileName':'Qux.kt'}");
  private static final String mappingResult =
      StringUtils.unixLines(
          "# {'id':'com.android.tools.r8.mapping','version':'2.2'}",
          "com.bar -> b:",
          "# {'id':'sourceFile','fileName':'Bar.kt'}",
          "com.baz -> c:",
          // TODO(b/286023274): We should not insert 'sourceFile' on composed classes.
          "# {'id':'sourceFile','fileName':'some-other-hash-inserted-into-source-file'}",
          "com.foo -> a:",
          "# {'id':'sourceFile','fileName':'Foo.kt'}",
          "com.qux -> d:",
          "# {'id':'sourceFile','fileName':'Qux.kt'}");

  @Test
  public void testCompose() throws Exception {
    ClassNameMapper mappingForFoo = ClassNameMapper.mapperFromString(mappingFoo);
    ClassNameMapper mappingForBar = ClassNameMapper.mapperFromString(mappingBar);
    String composed = MappingComposer.compose(mappingForFoo, mappingForBar);
    assertEquals(mappingResult, doubleToSingleQuote(composed));
  }
}
