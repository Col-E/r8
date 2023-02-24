// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.compilerapi.testsetup;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.compilerapi.CompilerApiTest;
import com.android.tools.r8.compilerapi.CompilerApiTestRunner;
import com.android.tools.r8.utils.InternalOptions;
import org.junit.Test;

public class ApiTestingSetUpTest extends CompilerApiTestRunner {

  public ApiTestingSetUpTest(TestParameters parameters) {
    super(parameters);
  }

  @Override
  public Class<? extends CompilerApiTest> binaryTestClass() {
    return ApiTest.class;
  }

  public static class ApiTest extends CompilerApiTest {

    public ApiTest(Object parameters) {
      super(parameters);
    }

    @Test
    public void testValidApiUse() throws Exception {
      R8Command.builder().setPrintHelp(true).build();
    }

    @Test
    public void testNonExistingApiUse() {
      try {
        new InternalOptions();
        // When running directly the class is public and visible.
        assertFalse(isRunningR8Lib());
      } catch (NoClassDefFoundError e) {
        // Internal options is not kept, so this access should fail externally.
        assertTrue(isRunningR8Lib());
      }
    }
  }
}
