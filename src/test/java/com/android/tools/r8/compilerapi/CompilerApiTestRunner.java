// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.compilerapi;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Base runner for all compiler API tests.
 *
 * <p>Using this runner will automatically create an externalized variant of the test. That is
 * useful to more quickly ensure the test itself is not using resources that are not available. Note
 * however, that it does not prevent using non-kept code in the compilers unless testing with r8lib!
 */
@RunWith(Parameterized.class)
public abstract class CompilerApiTestRunner extends TestBase {

  public abstract Class<? extends CompilerApiTest> binaryTestClass();

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public CompilerApiTestRunner(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void testExternal() throws Exception {
    new CompilerApiTestCollection(temp).runJunitOnTestClass(binaryTestClass());
  }
}
