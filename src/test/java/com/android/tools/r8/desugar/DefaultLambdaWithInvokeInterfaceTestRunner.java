// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;

public class DefaultLambdaWithInvokeInterfaceTestRunner extends TestBase {

  final Class<?> CLASS = DefaultLambdaWithInvokeInterfaceTest.class;
  final String EXPECTED = StringUtils.lines("4");

  @Test
  public void testJvm() throws Exception {
    testForJvm().addTestClasspath().run(CLASS).assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void test() throws Exception {
    testForD8()
        .addProgramClassesAndInnerClasses(CLASS)
        .setMinApi(AndroidApiLevel.K)
        .compile()
        // TODO(b/123506120): Add .assertNoMessages()
        .run(CLASS)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(inspector -> assertThat(inspector.clazz(CLASS), isPresent()));
  }
}
