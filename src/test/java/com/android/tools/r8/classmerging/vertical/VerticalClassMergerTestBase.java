// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.vertical;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;

public abstract class VerticalClassMergerTestBase extends TestBase {

  protected final TestParameters parameters;

  public VerticalClassMergerTestBase(TestParameters parameters) {
    this.parameters = parameters;
  }

  public void runDebugTest(Class<?> mainClass, R8TestCompileResult compileResult) throws Throwable {
    assertTrue(parameters.isDexRuntime());
    new VerticalClassMergerDebugTestRunner(mainClass.getTypeName(), temp)
        .run(parameters.getRuntime(), compileResult.app, compileResult.writeProguardMap());
  }
}
