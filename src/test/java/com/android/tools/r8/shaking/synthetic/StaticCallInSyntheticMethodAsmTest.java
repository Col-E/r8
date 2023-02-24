// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.synthetic;

import com.android.tools.r8.AsmTestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StaticCallInSyntheticMethodAsmTest extends AsmTestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  // class Base {
  //   static void foo() { ... }
  //   static synthetic void bar() { Sub.foo(); }
  // }
  // class Sub extends Base {}
  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassFileData(Base.dump(), Sub.dump())
        .addDontShrink()
        .setMinApi(parameters)
        .compile();
  }
}
