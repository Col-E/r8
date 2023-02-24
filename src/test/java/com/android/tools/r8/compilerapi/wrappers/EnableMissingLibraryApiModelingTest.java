// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.compilerapi.wrappers;

import com.android.tools.r8.D8Command;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.compilerapi.CompilerApiTest;
import com.android.tools.r8.compilerapi.CompilerApiTestRunner;
import org.junit.Test;

public class EnableMissingLibraryApiModelingTest extends CompilerApiTestRunner {

  public EnableMissingLibraryApiModelingTest(TestParameters parameters) {
    super(parameters);
  }

  @Override
  public Class<? extends CompilerApiTest> binaryTestClass() {
    return ApiTest.class;
  }

  @Test
  public void test() throws Exception {
    new ApiTest(ApiTest.PARAMETERS).run();
  }

  public static class ApiTest extends CompilerApiTest {

    public ApiTest(Object parameters) {
      super(parameters);
    }

    public void run() {
      D8Command.builder().setEnableExperimentalMissingLibraryApiModeling(true);
      R8Command.builder().setEnableExperimentalMissingLibraryApiModeling(true);
    }

    @Test
    public void test() throws Exception {
      run();
    }
  }
}
