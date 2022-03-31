// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.api;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public abstract class RetraceApiTestBase extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return TestParameters.builder().withNoneRuntime().build();
  }

  public RetraceApiTestBase(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  protected abstract Class<? extends RetraceApiBinaryTest> binaryTestClass();

  @Test
  public void testExternal() throws Exception {
    new RetraceApiTestCollection(temp).runJunitOnTestClass(binaryTestClass());
  }
}
